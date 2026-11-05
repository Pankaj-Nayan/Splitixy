# Splitixy 💜

**Split expenses. Stay organized. Settle up easily.**

Splitixy is a group expense management application designed to make sharing expenses simple and transparent. Whether you're travelling with friends, sharing accommodation, or managing everyday group expenses, Splitixy helps you track payments and understand who owes whom.

## ✨ Features

- **User Authentication** – Register and log in to your account.
- **Group Management** – Create groups and add members.
- **Expense Tracking** – Record shared expenses with descriptions and amounts.
- **Flexible Splitting** – Divide expenses equally or assign custom shares.
- **Balance Calculation** – Automatically calculate how much each member owes or is owed.
- **Who Owes Whom** – View outstanding balances between group members.
- **Expense History** – Review previously recorded expenses.
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

## 🚀 Getting Started

### Prerequisites

Make sure you have installed:

- Java (compatible with your Spring Boot version)
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

Set the `DB_PASSWORD` environment variable to your MySQL password.

### 3. Run the application

Using Maven:

```bash
mvn spring-boot:run
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
