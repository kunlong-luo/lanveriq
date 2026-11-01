#!/usr/bin/env python3
"""Secret-free classifier for a *private* synthetic admin bootstrap log.

Read the disposable runner log in memory, never print log lines, prompts,
values, passwords, SQL statements, stack traces or exception messages.
Only fixed literal diagnostic category labels are emitted to Actions.
"""
from pathlib import Path
import sys


def categories(raw: str) -> tuple[str, ...]:
    # Avoid relaying anything supplied by a process or error message.
    categories: list[str] = []
    patterns = (
        ("interactive_terminal_unavailable", ("Attached interactive terminal required for bootstrap",)),
        ("interactive_input_aborted", ("Password input aborted",)),
        ("password_confirmation_failed", ("Administrator password confirmation mismatch",)),
        ("login_or_group_validation", ("Invalid administrator login", "Invalid initial device-group name")),
        ("password_policy_rejected", ("Administrator password length must", "Administrator password contains")),
        ("offline_mode_rejected", ("Offline bootstrap requires web-application-type=none",)),
        ("spring_configuration_failure", ("Application run failed", "APPLICATION FAILED TO START", "BeanCreationException")),
        ("postgresql_connection_or_query_failure", ("PSQLException", "DataAccessException", "SQLGrammarException", "DataIntegrityViolationException")),
        ("postgresql_migration_failure", ("FlywayException", "FlywayValidateException", "FlywayMigrateException")),
        ("admin_already_exists", ("Initial administrator already exists",)),
        ("audit_or_role_transaction_failure", ("Initial administrator audit was not persisted", "Initial administrator grant was not persisted")),
        ("jpa_returning_or_result_failure", ("NonUniqueResultException", "NoResultException", "IllegalSelectQueryException", "QueryTypeMismatchException")),
        ("java_process_exception", ("Exception in thread", "java.lang.", "org.springframework.")),
    )
    for label, markers in patterns:
        if any(marker in raw for marker in markers):
            categories.append(label)
    # More specific Spring/bootstrap cause types. Never emit an exception's
    # message: it can include request parameters, SQL text or credentials.
    # Match only fully fixed class names or finite, machine-owned markers.
    boot_causes = (
        ("spring_bootstrap_constructor_selection",
         ("No default constructor found", "Failed to instantiate [com.lanveriq.server.security.FirstAdminBootstrapRunner]")),
        ("spring_missing_bean", ("NoSuchBeanDefinitionException", "UnsatisfiedDependencyException")),
        ("spring_bean_cycle", ("BeanCurrentlyInCreationException",)),
        ("spring_invalid_property", ("BindException", "ConfigurationPropertiesBindException", "Could not resolve placeholder")),
        ("spring_bean_initialization", ("BeanInstantiationException", "BeanCreationException")),
        ("spring_nonweb_security_dependency", ("No qualifying bean of type 'org.springframework.security.config.annotation.web.builders.HttpSecurity'",)),
        ("spring_context_missing_servlet", ("No ServletContext set", "No ServletWebServerFactory bean")),
        ("spring_context_property_missing", ("IllegalArgumentException: Could not resolve placeholder",)),
        ("jpa_entity_manager_failure", ("EntityManagerFactory", "entityManagerFactory", "PersistenceException")),
        ("db_authentication_failed", ("SQLSTATE 28P01", "password authentication failed for user")),
        ("db_connection_refused", ("Connection refused", "PSQLException: Connection to")),
        ("db_schema_validation_failed", ("SchemaManagementException", "Schema-validation", "SchemaValidationException")),
        ("transaction_exception", ("TransactionSystemException", "CannotCreateTransactionException", "UnexpectedRollbackException")),
        ("bootstrap_exception", ("FirstAdminBootstrapRunner.run", "FirstAdminBootstrapService.bootstrap")),
    )
    for label, markers in boot_causes:
        if any(marker in raw for marker in markers):
            categories.append(label)
    if "Initial administrator created." in raw:
        categories.append("application_reported_bootstrap_success")
    if "New administrator password:" in raw:
        categories.append("first_password_prompt_seen")
    if "Confirm administrator password:" in raw:
        categories.append("second_password_prompt_seen")
    return tuple(categories) or ("unclassified_offline_bootstrap_failure",)


def main() -> int:
    if len(sys.argv) != 2:
        print("synthetic_bootstrap_diagnostic: invalid_arguments", file=sys.stderr)
        return 2
    try:
        raw = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")[:300_000]
    except OSError:
        print("synthetic_bootstrap_diagnostic: private_log_unreadable", file=sys.stderr)
        return 1
    print("synthetic_bootstrap_diagnostic: " + ",".join(categories(raw)), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
