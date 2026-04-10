package janggi.infrastructure.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

public class TransactionTemplate {

    public void execute(Runnable action) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            try {
                connection.setAutoCommit(false);
                ConnectionContext.set(connection);
                action.run();
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                ConnectionContext.clear();
            }
        } catch (Exception e) {
            throw new RuntimeException("트랜잭션 실행 중 예외 발생", e);
        }
    }

    public <T> T execute(Supplier<T> action) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            try {
                connection.setAutoCommit(false);
                ConnectionContext.set(connection);
                T result = action.get();
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw new RuntimeException("트랜잭션 실행 중 예외 발생", e);
            } finally {
                ConnectionContext.clear();
            }
        } catch (SQLException e) {
            throw new RuntimeException("데이터베이스 연결 오류", e);
        }
    }
}
