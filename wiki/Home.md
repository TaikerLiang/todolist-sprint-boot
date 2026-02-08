# Todo List API - Developer Wiki

Welcome to the Todo List API documentation wiki! This wiki contains technical documentation, architecture decisions, and developer guides.

## 📚 Table of Contents

### Getting Started
- [Quick Start Guide](Quick-Start-Guide.md)
- [Development Environment Setup](Development-Environment-Setup.md)
- [Project Structure](Project-Structure.md)

### Architecture & Design
- [Project Constitution](Project-Constitution.md) - Core principles and standards
- [Layered Architecture](Layered-Architecture.md)
- [Database Schema](Database-Schema.md)

### Authentication & Security
- [JWT Authentication Overview](JWT-Authentication-Overview.md)
- [**Why Multiple Refresh Tokens?**](Why-Multiple-Refresh-Tokens.md) ⭐ **Start here!**
- [Spring Security Filter Chain](Spring-Security-Filter-Chain.md) - How requests are processed
- [Token Rotation & Security](Token-Rotation-Security.md)
- [Session Management](Session-Management.md)

### Development Guides
- [Database Migrations with Liquibase](Database-Migrations.md)
- [API Design Guidelines](API-Design-Guidelines.md)
- [Error Handling](Error-Handling.md)
- [Logging Standards](Logging-Standards.md)

### Testing
- [Testing Guide](Testing-Guide.md)
- [API Testing with cURL](API-Testing-cURL.md)

### Deployment
- [Environment Variables](Environment-Variables.md)
- [Production Deployment Checklist](Production-Deployment-Checklist.md)

---

## 🚀 Quick Links

- **API Documentation**: See [OpenAPI Specs](../specs/)
- **Implementation Summaries**: See [JWT Authentication Summary](../JWT_IMPLEMENTATION_SUMMARY.md)
- **Test Scripts**: See [test-jwt-auth.sh](../test-jwt-auth.sh)

## 📖 Recent Updates

- **2026-02-08**: Added JWT Authentication with token rotation
- **2026-02-08**: Created comprehensive wiki documentation

## 🤝 Contributing

When adding new features:
1. Update relevant wiki pages
2. Add OpenAPI specs to `/specs/`
3. Follow the [Project Constitution](Project-Constitution.md)
4. Run tests before committing

---

**Last Updated**: 2026-02-08
