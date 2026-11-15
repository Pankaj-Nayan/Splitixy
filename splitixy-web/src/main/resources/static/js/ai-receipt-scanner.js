(() => {
    const uploadForm = document.getElementById("receiptUploadForm");
    const fileInput = document.getElementById("receiptFile");
    if (uploadForm && fileInput) {
        const previewContainer = document.getElementById("previewContainer");
        const preview = document.getElementById("receiptPreview");
        const fileName = document.getElementById("selectedFileName");
        const scanButton = document.getElementById("scanButton");
        const loading = document.getElementById("scanLoading");
        let previewUrl = null;

        const releasePreview = () => {
            if (previewUrl) {
                URL.revokeObjectURL(previewUrl);
                previewUrl = null;
            }
            preview.removeAttribute("src");
            previewContainer.hidden = true;
            scanButton.disabled = true;
        };

        fileInput.addEventListener("change", () => {
            const file = fileInput.files && fileInput.files[0];
            releasePreview();
            if (!file) return;
            const allowed = /\.(jpe?g|png|webp)$/i.test(file.name)
                && file.size > 0 && file.size <= 10 * 1024 * 1024;
            if (!allowed) {
                fileInput.setCustomValidity("Choose a non-empty JPG, PNG, or WebP image up to 10 MB.");
                fileInput.reportValidity();
                fileInput.setCustomValidity("");
                fileInput.value = "";
                return;
            }
            previewUrl = URL.createObjectURL(file);
            preview.src = previewUrl;
            fileName.textContent = file.name;
            previewContainer.hidden = false;
            scanButton.disabled = false;
        });

        document.getElementById("removeReceipt").addEventListener("click", () => {
            releasePreview();
            fileInput.value = "";
        });
        uploadForm.addEventListener("submit", () => {
            if (!fileInput.files || !fileInput.files.length) return;
            scanButton.disabled = true;
            loading.hidden = false;
        });
        window.addEventListener("pagehide", () => {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
        });
    }

    const form = document.getElementById("receiptReviewForm");
    if (!form) return;

    const itemEditor = document.getElementById("receiptItemEditor");
    const itemsInput = document.getElementById("receiptItems");
    const participantInputs = [...form.querySelectorAll(".participant-checkbox")];
    const members = participantInputs.map(input => ({
        id: Number(input.value),
        name: input.closest("label").querySelector("span").textContent.trim()
    }));
    const methodInputs = [...form.querySelectorAll('input[name="splittingMethod"]')];
    const totalSplitOptions = document.getElementById("totalSplitOptions");
    const memberShareInputs = document.getElementById("memberShareInputs");
    const itemAdjustments = document.getElementById("itemAdjustments");
    const overrides = document.getElementById("itemShareOverrides");
    const summary = document.getElementById("memberShareSummary");
    const finalShares = document.getElementById("confirmationShares");
    const discrepancy = document.getElementById("reconciliationNotice");
    const otherAdjustment = document.getElementById("otherAdjustment");
    const cents = value => {
        if (value === null || value === undefined || value === "") return 0n;
        const match = String(value).trim().match(/^(-?)(\d+)(?:\.(\d{1,2}))?$/);
        if (!match) return 0n;
        const fraction = (match[3] || "").padEnd(2, "0");
        const result = BigInt(match[2]) * 100n + BigInt(fraction || "0");
        return match[1] ? -result : result;
    };
    const money = value => {
        const negative = value < 0n;
        const absolute = negative ? -value : value;
        return `${negative ? "-" : ""}${absolute / 100n}.${String(absolute % 100n).padStart(2, "0")}`;
    };
    const valueOf = name => {
        const input = form.elements.namedItem(name);
        return input ? input.value : "";
    };
    const selectedIds = () => participantInputs
        .filter(input => input.checked).map(input => Number(input.value));
    const chosenMethod = () => methodInputs.find(input => input.checked)?.value || "TOTAL";
    let itemRows = [];

    const createField = (labelText, value, type, step) => {
        const wrapper = document.createElement("div");
        wrapper.className = "item-field";
        const label = document.createElement("label");
        label.textContent = labelText;
        const input = document.createElement("input");
        input.type = type;
        input.value = value ?? "";
        if (type === "number") {
            input.min = labelText === "Quantity" ? "0.001" : "0";
            input.step = step || "0.01";
        } else {
            input.maxLength = 160;
        }
        wrapper.append(label, input);
        return {wrapper, input};
    };

    const addItem = item => {
        const row = document.createElement("article");
        row.className = "receipt-item-row";
        const name = createField("Item name", item.name, "text");
        name.wrapper.classList.add("item-name-field");
        const quantity = createField("Quantity", item.quantity ?? "1", "number", "0.001");
        const unit = createField("Unit price", item.unitPrice, "number");
        const total = createField("Line total", item.totalPrice, "number");
        const discount = createField("Item discount", item.discount, "number");
        const remove = document.createElement("button");
        remove.className = "remove-item";
        remove.type = "button";
        remove.textContent = "Remove";
        const assignments = document.createElement("div");
        assignments.className = "item-assignees";
        assignments.setAttribute("aria-label", "Members sharing this item");
        members.forEach(member => {
            const label = document.createElement("label");
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.dataset.memberId = String(member.id);
            checkbox.checked = Array.isArray(item.participantIds)
                && item.participantIds.includes(member.id);
            checkbox.disabled = !selectedIds().includes(member.id);
            const nameText = document.createElement("span");
            nameText.textContent = member.name;
            label.append(checkbox, nameText);
            assignments.append(label);
        });
        const rowData = {row, name: name.input, quantity: quantity.input,
            unit: unit.input, total: total.input, discount: discount.input, assignments};
        [name.input, quantity.input, unit.input, total.input, discount.input]
            .forEach(input => input.addEventListener("input", update));
        assignments.addEventListener("change", update);
        remove.addEventListener("click", () => {
            row.remove();
            itemRows = itemRows.filter(current => current !== rowData);
            update();
        });
        row.append(name.wrapper, quantity.wrapper, unit.wrapper, total.wrapper,
            discount.wrapper, remove, assignments);
        itemEditor.append(row);
        itemRows.push(rowData);
    };

    const serializeItems = () => itemRows.map(item => ({
        name: item.name.value.trim(),
        quantity: item.quantity.value,
        unitPrice: item.unit.value || null,
        totalPrice: item.total.value || null,
        discount: item.discount.value || "0",
        participantIds: [...item.assignments.querySelectorAll("input:checked")]
            .map(input => Number(input.dataset.memberId))
    }));

    const distribute = (amount, weights) => {
        const ids = [...weights.keys()].sort((a, b) => a - b);
        const totalWeight = ids.reduce((sum, id) => sum + weights.get(id), 0n);
        const results = new Map(ids.map(id => [id, 0n]));
        if (!ids.length || !totalWeight || amount === 0n) return results;
        const sign = amount < 0n ? -1n : 1n;
        const absolute = amount < 0n ? -amount : amount;
        const portions = ids.map(id => {
            const numerator = absolute * weights.get(id);
            return {id, amount: numerator / totalWeight, remainder: numerator % totalWeight};
        });
        let remaining = absolute - portions.reduce((sum, part) => sum + part.amount, 0n);
        portions.sort((a, b) => a.remainder === b.remainder
            ? a.id - b.id : a.remainder > b.remainder ? -1 : 1);
        for (let i = 0; remaining > 0n; i++, remaining--) portions[i].amount++;
        portions.forEach(part => results.set(part.id, part.amount * sign));
        return results;
    };

    const equalDistribution = (amount, ids) => {
        const sorted = [...ids].sort((a, b) => a - b);
        const result = new Map(sorted.map(id => [id, 0n]));
        if (!sorted.length) return result;
        const base = amount / BigInt(sorted.length);
        let remainder = amount % BigInt(sorted.length);
        sorted.forEach(id => {
            result.set(id, base + (remainder > 0n ? 1n : 0n));
            if (remainder > 0n) remainder--;
        });
        return result;
    };

    const renderShares = shares => {
        const rows = [...shares.entries()]
            .map(([id, amount]) => {
                const row = document.createElement("div");
                const person = document.createElement("span");
                person.textContent = members.find(member => member.id === id)?.name || "Member";
                const value = document.createElement("strong");
                value.textContent = money(amount);
                row.append(person, value);
                return row;
            });
        summary.replaceChildren(...rows);
        finalShares.replaceChildren(...rows.map(row => row.cloneNode(true)));
    };

    const itemShares = ids => {
        const shares = new Map(ids.map(id => [id, 0n]));
        const weights = new Map(ids.map(id => [id, 0n]));
        let itemGross = 0n;
        let itemNet = 0n;
        itemRows.forEach(item => {
            const line = cents(item.total.value);
            const lineDiscount = cents(item.discount.value);
            const net = line - lineDiscount;
            itemGross += line;
            itemNet += net;
            const assignees = [...item.assignments.querySelectorAll("input:checked")]
                .map(input => Number(input.dataset.memberId)).filter(id => ids.includes(id));
            const split = equalDistribution(net, assignees);
            split.forEach((value, id) => {
                shares.set(id, (shares.get(id) || 0n) + value);
                weights.set(id, (weights.get(id) || 0n) + value);
            });
        });
        const extras = [
            {key: "tax", amount: cents(valueOf("tax"))},
            {key: "tip", amount: cents(valueOf("tip"))},
            {key: "serviceCharge", amount: cents(valueOf("serviceCharge"))},
            {key: "discount", amount: -cents(valueOf("discount"))}
        ];
        extras.forEach(({key, amount}) => {
            const manual = new Map();
            let hasManualValue = false;
            ids.forEach(id => {
                const input = form.elements.namedItem(`allocation_${key}_${id}`);
                if (input && input.value !== "") {
                    hasManualValue = true;
                    manual.set(id, cents(input.value) * (key === "discount" ? -1n : 1n));
                }
            });
            const allocation = hasManualValue && manual.size === ids.length
                ? manual : distribute(amount, weights);
            allocation.forEach((value, id) =>
                shares.set(id, (shares.get(id) || 0n) + value));
        });
        const expected = itemNet + extras.reduce((sum, value) => sum + value.amount, 0n);
        const difference = cents(valueOf("finalTotal")) - expected;
        const subtotal = cents(valueOf("subtotal"));
        if (subtotal && subtotal !== itemGross) {
            discrepancy.hidden = false;
            discrepancy.textContent = `Receipt subtotal (${money(subtotal)}) differs from the sum of item totals (${money(itemGross)}). Review the values.`;
        } else if (difference) {
            discrepancy.hidden = false;
            discrepancy.textContent = `The item totals and charges differ from the final total by ${money(difference)}. Review the receipt or enter this exact value as the receipt difference below.`;
        } else {
            discrepancy.hidden = true;
            discrepancy.textContent = "";
        }
        if (difference) {
            otherAdjustment.setAttribute("aria-describedby", "reconciliationNotice");
        } else {
            otherAdjustment.removeAttribute("aria-describedby");
        }
        distribute(difference, weights).forEach((value, id) =>
            shares.set(id, (shares.get(id) || 0n) + value));
        return shares;
    };

    const update = () => {
        itemsInput.value = JSON.stringify(serializeItems());
        const selected = selectedIds();
        const method = chosenMethod();
        const totalMode = method === "TOTAL";
        const custom = document.getElementById("splitType").value === "CUSTOM";
        totalSplitOptions.hidden = !totalMode;
        memberShareInputs.hidden = !(totalMode && custom);
        itemAdjustments.hidden = totalMode;
        document.querySelectorAll(".total-share-input").forEach(input => {
            input.closest(".member-share-row").hidden = !selected.includes(
                Number(input.name.substring("share_".length)));
        });
        document.querySelectorAll(".item-share-input").forEach(input => {
            input.closest(".member-share-row").hidden = !selected.includes(
                Number(input.name.substring("itemShare_".length)));
        });
        document.querySelectorAll(".adjustment-allocation-row").forEach(row => {
            row.hidden = !selected.includes(Number(row.dataset.memberId));
        });
        itemRows.forEach(item => item.assignments.querySelectorAll("input").forEach(input => {
            const selectedNow = selected.includes(Number(input.dataset.memberId));
            if (!selectedNow) input.checked = false;
            input.disabled = !selectedNow;
        }));

        let shares = new Map(selected.map(id => [id, 0n]));
        if (totalMode) {
            const total = cents(valueOf("finalTotal"));
            if (custom) {
                document.querySelectorAll(".total-share-input").forEach(input => {
                    if (!input.closest(".member-share-row").hidden) {
                        shares.set(Number(input.name.substring("share_".length)), cents(input.value));
                    }
                });
            } else {
                shares = equalDistribution(total, selected);
            }
            discrepancy.hidden = true;
        } else {
            shares = itemShares(selected);
            document.querySelectorAll(".item-share-input").forEach(input => {
                if (!input.closest(".member-share-row").hidden && input.value !== "") {
                    shares.set(Number(input.name.substring("itemShare_".length)), cents(input.value));
                }
            });
        }
        renderShares(shares);

        const merchant = valueOf("merchantName").trim() || "Receipt expense";
        document.getElementById("summaryMerchant").textContent = merchant;
        const total = valueOf("finalTotal");
        document.getElementById("summaryTotal").textContent = total
            ? `${valueOf("currency") || ""} ${total}`.trim() : "Needs review";
        const payer = form.elements.namedItem("payerId");
        document.getElementById("summaryPayer").textContent =
            payer?.selectedOptions[0]?.textContent || "Needs selection";
        document.getElementById("summaryParticipants").textContent = selected.length
            ? selected.map(id => members.find(member => member.id === id)?.name || "Member").join(", ")
            : "Needs selection";
        document.getElementById("summaryMethod").textContent = totalMode
            ? `Split Entire Bill (${custom ? "custom" : "equal"})`
            : "Split Individual Items";
    };

    try {
        const initial = JSON.parse(itemsInput.value || "[]");
        if (Array.isArray(initial)) initial.forEach(addItem);
    } catch (error) {
        itemsInput.value = "[]";
    }
    document.getElementById("addReceiptItem").addEventListener("click", () => {
        addItem({quantity: "1", discount: "0", participantIds: []});
        update();
    });
    participantInputs.forEach(input => input.addEventListener("change", update));
    methodInputs.forEach(input => input.addEventListener("change", update));
    form.elements.namedItem("splitType").addEventListener("change", update);
    form.querySelectorAll("input, select").forEach(input => {
        if (!input.classList.contains("participant-checkbox")
                && !input.classList.contains("total-share-input")
                && !input.classList.contains("item-share-input")
                && input.id !== "receiptItems") {
            input.addEventListener("input", update);
            input.addEventListener("change", update);
        }
    });
    form.addEventListener("submit", () => {
        itemsInput.value = JSON.stringify(serializeItems());
    });
    update();
})();
