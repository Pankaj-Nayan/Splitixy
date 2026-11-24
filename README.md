# Splitixy 💜

**Split expenses. Stay organized. Settle up easily.**

Splitixy is a group expense management application designed to make sharing expenses simple and transparent. Whether you're travelling with friends, sharing accommodation, or managing everyday group expenses, Splitixy helps you track payments and understand who owes whom.

## ✨ Features

- **User Authentication** – Register and log in to your account.
- **Group Management** – Create groups and add members.
- **Expense Tracking** – Record shared expenses with descriptions and amounts.
- **AI Expense Assistant** – Describe a group expense in natural language, review the extracted details, and confirm before it is saved.
- **AI Receipt Scanner** – Upload a receipt image for Gemini extraction, review and edit its details, then split the bill equally, by custom shares, or across assigned items.
- **Flexible Splitting** – Divide expenses equally or assign custom shares.
- **Balance Calculation** – Automatically calculate how much each member owes or is owed.
- **Who Owes Whom** – View outstanding balances between group members.
- **Expense History** – Review previously recorded expenses.
- **Smart Spending Insights** – Explore personal shares, category breakdowns, monthly trends, unusual spending alerts, and optional AI commentary.
- **User Profile** – View your account information and expense-related details.
- **Dashboard** – Access your groups, expenses, and financial summaries.

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Java | Core programming language |
| Spring Boot | Backend application framework |
| Spring MVC | Web request handling |
| Spring Data JPA | Database operations |
| Hibernate | ORM |
| Spring Security | Authentication and security |
| MySQL | Relational database |
| Thymeleaf | Server-side HTML rendering |
| HTML, CSS, JavaScript | Frontend |
| Bootstrap | Responsive UI |
| Maven | Dependency and build management |
| Git & GitHub | Version control |

## Docker Compose (recommended)

### Prerequisites

- Git
- Docker Desktop (or Docker Engine with the Docker Compose plugin) running

Java, Maven, and MySQL are not required on the host when using Docker Compose.

### 1. Clone the repository

```bash
git clone https://github.com/Pankaj-Nayan/Splitixy.git
cd Splitixy
```

### 2. Configure environment variables

Copy `.env.example` to `.env` (for example, `Copy-Item .env.example .env` in PowerShell, or `cp .env.example .env` in Bash), then edit `.env`. Replace both database password placeholders with strong, different passwords. Supply valid GitHub and Google OAuth client IDs and secrets; those credentials are required for the application to start with its configured OAuth providers. The Gemini and Resend credentials are optional, but their related AI and email features need valid credentials.

The root `.env` file is ignored by Git and is excluded from the Docker build context. Never commit real credentials.
Credentials that were previously committed remain in Git history; revoke and rotate any that were real, and follow your repository's process for removing sensitive history if needed.

### 3. Start Splitixy

```bash
docker compose up --build -d
docker compose logs -f app
```

Open [http://localhost:6969](http://localhost:6969). MySQL is available to host tools at `127.0.0.1:3307` by default; change `MYSQL_PORT` in `.env` if that port is already in use.

### 4. Stop, restart, and keep data

Stop the containers with `docker compose down`. The named `mysql_data` volume is retained, so `docker compose up -d` starts again with the same Docker database. To rebuild and restart the app after code changes, run `docker compose up --build -d app`. Avoid `docker compose down -v` unless you intend to delete the Docker database volume.

Hibernate is configured with `ddl-auto=update`; on startup it creates or updates tables from the JPA entities. This is not a data migration: the Docker database starts separately and does not include records from a developer's existing local MySQL database. To preserve local records, create and restore a database dump into the Docker MySQL instance before relying on it. No local database is modified by these Docker commands.

## 🚀 Manual Development Setup

### Prerequisites

For running the application directly on the host, install:

- Java 21
- Maven
- MySQL
- Git

### 1. Clone the repository

```bash
git clone https://github.com/Pankaj-Nayan/Splitixy.git
cd Splitixy
```

### 2. Configure the database

Create a MySQL database for Splitixy.

```sql
CREATE DATABASE splitixy;
```

Configure your database connection in `application.properties`.

For example:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/splitixy
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD}
```

Set the `DB_PASSWORD` environment variable to your local MySQL password. Also set `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, and `GOOGLE_CLIENT_SECRET` for the configured OAuth providers. `GEMINI_API_KEY` and `RESEND_API_KEY` are needed for their respective AI and email features.

### 3. Run the application

Set `GEMINI_API_KEY` in the environment where the application runs. AI features call
Gemini from the Spring Boot backend; the key is not sent to the browser. AI-assisted
expenses currently use INR to match Splitixy's rupee-based balances. Receipt images are
not retained by Splitixy. Multiple payers must be recorded as separate expenses because
the existing expense model has one payer.

Using Maven:

```bash
cd splitixy-web
./mvnw spring-boot:run
```

### 4. Open the application

Visit:

```text
http://localhost:6969
```

## 📂 Project Structure

```text
Splitixy/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/splitixy/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── entity/
│   │   │       └── config/
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── css/
│   │       │   ├── js/
│   │       │   └── images/
│   │       ├── templates/
│   │       └── application.properties
│   └── test/
├── pom.xml
└── README.md
```

*The package and folder names above represent the intended organization; adjust them if your current project structure differs.*

## 💡 How It Works

1. Create an account and log in.
2. Create a group and add members.
3. Record an expense and select who paid.
4. Choose an equal or custom split.
5. Splitixy calculates each member's share.
6. View balances to understand who owes whom.

## 🎯 Project Goal

The goal of Splitixy is to simplify shared financial management by reducing manual calculations, keeping expense records organized, and making group settlements easier to understand.

## 👨‍💻 Author

**Pankaj Nayan**

GitHub: [@Pankaj-Nayan](https://github.com/Pankaj-Nayan)

---

*Built with Java, Spring Boot, and a focus on making shared expenses simpler.*
