package com.schoollink.shared.jdbc;

import com.schoollink.shared.error.ApiException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/**
 * Runs a statement whose database constraints may reject it, and turns a violation into an
 * {@link ApiException} chosen from the PostgreSQL SQL state (23505 unique, 23503 foreign key,
 * 23P01 exclusion). The statement runs in a savepoint, so a violation does not abort the
 * enclosing transaction. Must be called inside a transaction.
 */
@Component
public class ConstraintGuard {

	public static final String UNIQUE = "23505";

	public static final String FOREIGN_KEY = "23503";

	public static final String EXCLUSION = "23P01";

	private final DataSource dataSource;

	ConstraintGuard(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/** @param errors SQL state -> error to throw, or {@code null} to rethrow the violation */
	public <T> T run(Supplier<T> statement, Function<String, ApiException> errors) {
		Connection connection = DataSourceUtils.getConnection(dataSource); // the transaction's connection
		Savepoint savepoint = sql(connection::setSavepoint);
		try {
			T result = statement.get();
			sql(() -> {
				connection.releaseSavepoint(savepoint);
				return null;
			});
			return result;
		}
		catch (DataIntegrityViolationException ex) {
			sql(() -> {
				connection.rollback(savepoint);
				return null;
			});
			String state = NestedExceptionUtils.getMostSpecificCause(ex) instanceof SQLException e ? e.getSQLState() : "";
			ApiException error = errors.apply(state);
			throw error != null ? error : ex;
		}
	}

	private interface SqlCall<T> {

		T call() throws SQLException;

	}

	private static <T> T sql(SqlCall<T> call) {
		try {
			return call.call();
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Savepoint failed", ex);
		}
	}

}
