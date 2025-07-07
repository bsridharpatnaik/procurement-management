# Procurement Management System

A comprehensive multi-tenant procurement management REST API designed for Sun Group's manufacturing facilities with centralized purchase team management.

## 🏭 System Overview

The Procurement Management System provides REST APIs to streamline the entire procurement workflow from request creation to delivery confirmation across multiple manufacturing facilities. It features role-based access control, comprehensive audit trails, and complete business logic implementation.

## ✨ Key Features

### 🔐 User Management & Authentication
- **Role-based Access Control**: Admin, Factory User, Purchase Team, Management
- **JWT Authentication**: Secure token-based authentication
- **Multi-factory Support**: Factory users restricted to their assigned facilities

### 📋 Procurement Workflow
- **Complete Lifecycle Management**: From draft to closed status
- **Approval Workflow**: Management approval for flagged requests
- **Assignment System**: Requests assigned to purchase team members
- **Short Close Capability**: Partial fulfillment with reason tracking

### 🏢 Multi-Entity Management
- **Factories**: 5 pre-configured facilities with extensibility
- **Materials**: Shared material catalog with import tracking
- **Vendors**: Centralized vendor management (purchase team only)
- **Line Items**: Detailed procurement line item tracking

### 📊 Dashboard & Reporting
- **Role-specific Dashboards**: Tailored views for each user type
- **Advanced Filtering**: Filter by status, factory, priority, dates, etc.
- **History & Audit**: Complete audit trail with JPA Envers
- **CSV Export**: Export filtered data for reporting

### 🔔 Notifications
- **Real-time Updates**: Designed for in-app notifications for status changes
- **Role-based Alerts**: Targeted notifications based on user roles
- **Audit Events**: System events for tracking changes

## 🛠 Technology Stack

- **Backend**: Spring Boot 2.x
- **Database**: MySQL
- **Authentication**: JWT
- **Audit Trail**: JPA Envers
- **Build Tool**: Maven
- **Documentation**: Swagger/OpenAPI

## 📋 Prerequisites

Before running the application, ensure you have the following installed:

- **Java 11** or higher
- **MySQL 8.0** or higher
- **Maven 3.6** or higher
- **Git**

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/procurement-management-system.git
cd procurement-management-system
```

### 2. Database Setup

Create a MySQL database and update the configuration:

```sql
CREATE DATABASE procurement_db;
CREATE USER 'procurement_user'@'localhost' IDENTIFIED BY 'procurement_password';
GRANT ALL PRIVILEGES ON procurement_db.* TO 'procurement_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Application Configuration

Update `src/main/resources/application.properties`:

```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/procurement_db
spring.datasource.username=procurement_user
spring.datasource.password=procurement_password

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# JWT Configuration
jwt.secret=your-secret-key
jwt.expiration=86400

# Server Configuration
server.port=8080
```

### 4. Run the Application

```bash
# Install dependencies and run
mvn clean install
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## 📊 API Documentation

Once the application is running, access the Swagger documentation at:
```
http://localhost:8080/swagger-ui.html
```

## 👥 Default Users

The system initializes with the following default users:

| Role | Username | Password | Description |
|------|----------|----------|-------------|
| Admin | `admin` | `admin123` | System administrator |
| Management | `amit.sir` | `amit123` | Management team member |
| Purchase Team | `rakesh` | `rakesh123` | Purchase manager |
| Purchase Team | `nisha` | `nisha123` | Purchase team member |
| Purchase Team | `vartika` | `vartika123` | Purchase team member |
| Purchase Team | `neha` | `neha123` | Purchase team member |
| Factory User | `factory.tc` | `factory123` | Thermocare plant manager |

## 🏭 Pre-configured Factories

1. **THERMOCARE ROCKWOOL INDIA PVT. LTD.** (Code: TC)
2. **SUNTECH GEOTEXTILE PVT. LTD** (Code: SG)
3. **NAAD INDUSTRIES PVT. LTD** (Code: NI)
4. **NAAD NONWOVEN PVT. LTD** (Code: NN)
5. **GEOPOL INDUSTRIES PVT. LTD** (Code: GP)

## 📊 Procurement Workflow

```
DRAFT → SUBMITTED → IN_PROGRESS → ORDERED → DISPATCHED → RECEIVED → CLOSED
```

### Status Descriptions:
- **DRAFT**: Factory user creates and saves request
- **SUBMITTED**: Factory user submits request
- **IN_PROGRESS**: Purchase team assigns and starts working
- **ORDERED**: Purchase team assigns vendor and price
- **DISPATCHED**: Purchase team marks as sent to factory
- **RECEIVED**: Factory team confirms receipt
- **CLOSED**: Final status

## 📁 Project Structure

```
procurement-management-system/
├── src/main/java/com/sungroup/procurement/
│   ├── controller/              # REST Controllers
│   ├── service/                 # Business Logic
│   ├── repository/              # Data Access Layer
│   ├── entity/                  # JPA Entities
│   ├── dto/                     # Data Transfer Objects
│   ├── config/                  # Configuration Classes
│   ├── constants/               # Application Constants
│   ├── specification/           # JPA Specifications
│   └── exception/               # Custom Exceptions
├── src/main/resources/
│   ├── application.properties
│   └── data.sql
├── pom.xml
└── README.md
```

## 🧪 Testing

```bash
mvn test
```

## 🔍 Key Features in Detail

### Material Management
- Shared material catalog across all factories
- Import from China flag for lead time tracking
- Unit of measurement tracking
- Price history maintenance

### Vendor Management
- Centralized vendor database
- Contact information management
- Price history tracking per vendor-material combination
- Restricted access (Purchase Team and Management only)

### Advanced Filtering
- Date range filtering
- Status-based filtering
- Factory-specific filtering
- Material and vendor filtering
- Pending days filtering
- Priority-based filtering

### Audit Trail
- Complete change history using JPA Envers
- User activity tracking
- Historical data preservation
- Compliance-ready audit logs

## 🚀 Deployment

### Production Build

```bash
mvn clean package -Pprod
```

The generated JAR file will be available in the `target/` directory.



## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Support

For questions about the system:
- Check the API documentation at `http://localhost:8080/swagger-ui.html`
- Review the user roles and workflow sections above

## 🚀 Future Enhancements

- **API-based System**: Complete REST API for frontend integration
- **Advanced reporting and analytics**
- **Integration with ERP systems**
- **Supplier portal APIs**
- **Automated purchase order generation**
- **Advanced approval workflows**
- **Real-time inventory tracking APIs**

---

**Note**: This is a backend REST API system. A frontend application would need to be developed separately to consume these APIs.
