/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved.
 *
 * This file is part of the QuickFIX FIX Engine
 *
 * This file may be distributed under the terms of the quickfixengine.org
 * license as defined by quickfixengine.org and appearing in the file
 * LICENSE included in the packaging of this file.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information.
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing
 * are not clear to you.
 ******************************************************************************/

package quickfix;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JdbcTestSupport {

    public static final String HSQL_DRIVER = "org.hsqldb.jdbcDriver";
    public static final String HSQL_CONNECTION_URL = "jdbc:hsqldb:mem:quickfixj";
    public static final String HSQL_USER = "sa";

    public static void setHypersonicSettings(SessionSettings settings) {
        settings.setString(JdbcSetting.SETTING_JDBC_DRIVER, HSQL_DRIVER);
        settings.setString(JdbcSetting.SETTING_JDBC_CONNECTION_URL, HSQL_CONNECTION_URL);
        settings.setString(JdbcSetting.SETTING_JDBC_USER, HSQL_USER);
        settings.setString(JdbcSetting.SETTING_JDBC_PASSWORD, "");
    }

    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName(HSQL_DRIVER);
        return DriverManager.getConnection(HSQL_CONNECTION_URL, HSQL_USER, "");
    }

    public static class HypersonicPreprocessor {
        private final String tableName;

        public HypersonicPreprocessor(String tableName) {
            this.tableName = tableName;
        }

        public String preprocessSQL(String sql) {
            String preprocessedSql = sql;
            preprocessedSql = preprocessedSql.replaceAll("USE .*;", "");
            preprocessedSql = preprocessedSql.replaceAll(" UNSIGNED", "");
            preprocessedSql = preprocessedSql.replaceAll("AUTO_INCREMENT", "IDENTITY");
            preprocessedSql = preprocessedSql.replaceAll("TEXT", "VARCHAR(256)");
            if (tableName != null) {
                preprocessedSql = preprocessedSql.replaceAll("CREATE TABLE [a-z]+", "CREATE TABLE "
                        + tableName);
                preprocessedSql = preprocessedSql.replaceAll("DROP TABLE [a-z]+", "DROP TABLE "
                        + tableName);
                preprocessedSql = preprocessedSql.replaceAll("DELETE FROM [a-z]+", "DELETE FROM "
                        + tableName);
            }
            return preprocessedSql;
        }
    }

    public static class HypersonicLegacyPreprocessor extends HypersonicPreprocessor {

        public HypersonicLegacyPreprocessor(String tableName) {
            super(tableName);
        }

        public String preprocessSQL(String sql) {
            return super.preprocessSQL(sql).replaceAll(" +(sender|target)(subid|locid).*,", "");
        }
    }

    public static void loadSQL(Connection connection, String resource,
            HypersonicPreprocessor sqlPreprocessor) throws SQLException, IOException {
        Statement stmt = connection.createStatement();
        InputStream sqlInput = FileUtil.open(Message.class, resource);
        String sql = getString(sqlInput);
        if (sqlPreprocessor != null) {
            sql = sqlPreprocessor.preprocessSQL(sql);
        }
        stmt.execute(sql);
        stmt.close();
    }

    public static void dropTable(Connection connection, String tableName) throws SQLException,
            IOException {
        execSQL(connection, "drop table " + tableName + " if exists");
    }

    public static void execSQL(Connection connection, String sql) throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute(sql);
        stmt.close();
    }

    private static String getString(InputStream in) throws IOException {
        int n = in.available();
        byte[] b = new byte[n];
        in.read(b);
        return new String(b);
    }

    static void assertNoActiveConnections(DataSource dataSource) {
        assertTrue(dataSource instanceof HikariDataSource);

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertEquals("Some connections are still alive", 0, hikariDataSource.getHikariPoolMXBean().getActiveConnections());
    }

    static DataSource getTestDataSource(String jdbcDriver, String connectionURL, String user, String password) {
        SessionID sessionID = new SessionID("TEST", "", "");

        SessionSettings settings = new SessionSettings();
        // HSQL doesn't support JDBC4 which means that test query has to be supplied to HikariCP
        settings.setString(sessionID, JdbcSetting.SETTING_JDBC_CONNECTION_TEST_QUERY, "SELECT COUNT(1) FROM INFORMATION_SCHEMA.SYSTEM_USERS WHERE 1 = 0;");

        try {
            return JdbcUtil.getOrCreatePooledDataSource(settings, sessionID, jdbcDriver, connectionURL, user, password);
        } catch (ConfigError | FieldConvertError e) {
            throw new RuntimeException("Unable to get or create pooled data source", e);
        }
    }
}
