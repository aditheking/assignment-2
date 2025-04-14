# Bidirectional ClickHouse & Flat File Data Ingestion Tool

This project is a web-based application that facilitates data ingestion between a ClickHouse database and Flat Files.


![Screenshot 2025-04-14 220419](https://github.com/user-attachments/assets/f8297da6-6313-4621-b372-a9c9c2052063)

## Features

*   Bidirectional data flow (ClickHouse <-> Flat File).
*   JWT token authentication for ClickHouse as a source.
*   Dynamic table and column selection from the source.
*   Ingestion progress status and completion reporting (record count).
*   Basic error handling.
*   (Optional) Data preview.

## Technology Stack

*   **Backend:** Java 17, Spring Boot 3.x
*   **Frontend:** HTML, CSS, JavaScript, Thymeleaf
*   **Database:** ClickHouse
*   **Build Tool:** Maven
*   **Libraries:**
    *   `spring-boot-starter-web`: For building web applications.
    *   `spring-boot-starter-thymeleaf`: For server-side HTML templating.
    *   `clickhouse-jdbc`: Official ClickHouse JDBC driver (using `all` classifier for dependencies).
    *   `opencsv`: For reading/writing CSV files.

## Setup

1.  **Prerequisites:**
    *   Java Development Kit (JDK) 17 or later.
    *   Maven 3.6 or later.
    *   Access to a ClickHouse instance (local via Docker or cloud).
    *   (Optional) Load ClickHouse example datasets (`uk_price_paid`, `ontime`).

2.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd integration-tool
    ```

3.  **Build the project:**
    ```bash
    mvn clean package
    ```

## Configuration

*   Connection details (ClickHouse host, port, database, user, token, Flat File path, delimiter) are configured via the web UI.
*   Default application settings can be reviewed in `src/main/resources/application.properties` (though UI inputs take precedence for connections).

## Running the Application

1.  **Run using Spring Boot Maven plugin:**
    ```bash
    mvn spring-boot:run
    ```
2.  **Run the packaged JAR:**
    ```bash
    java -jar target/integration-tool-1.0-SNAPSHOT.jar
    ```

Once running, access the application in your web browser, typically at `http://localhost:8080`.

## Usage

1.  Open the application in your browser.
2.  Select the ingestion direction (ClickHouse to Flat File or vice-versa).
3.  Enter the required connection parameters for the **source**.
4.  **ClickHouse as Source:**
    *   Click "Connect & List Tables".
    *   Select the desired table from the dropdown.
    *   Click "Load Columns".
    *   Select the columns you want to ingest.
    *   Enter the target Flat File path and delimiter.
5.  **Flat File as Source:** 
    *   Provide Flat File details (path, delimiter).
    *   Specify target ClickHouse table details.
    *   Column mapping/selection will be required.
6.  (Optional) Click "Preview Data" to see the first 100 records.
7.  Click "Start Ingestion".
8.  Monitor the status and view the results (record count or error message).


 
