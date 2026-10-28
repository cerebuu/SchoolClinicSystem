package clinic.persistence;

import java.sql.*;
import java.util.*;

final class Sql {
    static boolean mysql(Connection c) throws SQLException {
        String product=c.getMetaData().getDatabaseProductName();
        return product.equalsIgnoreCase("MySQL")||product.equalsIgnoreCase("MariaDB");
    }
    static boolean mariadb(Connection c) throws SQLException {
        // Connector/J reports the product as MySQL even when the server is MariaDB.
        var metadata=c.getMetaData();
        return (metadata.getDatabaseProductName()+" "+metadata.getDatabaseProductVersion())
            .toLowerCase(Locale.ROOT).contains("mariadb");
    }
    static String text(Connection c,String expression) throws SQLException {
        return "CAST("+expression+" AS "+(mysql(c)?"CHAR":"VARCHAR")+")";
    }
    @FunctionalInterface interface Row<T> { T read(ResultSet row) throws SQLException; }
    static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) { bind(s,args); return s.executeUpdate(); }
    }
    static <T> List<T> query(Connection c, String sql, Row<T> map, Object... args) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s,args);
            try (ResultSet r=s.executeQuery()) {
                List<T> result=new ArrayList<>();
                while(r.next()) result.add(map.read(r));
                return result;
            }
        }
    }
    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        boolean microseconds=mysql(statement.getConnection());
        for(int i=0;i<args.length;i++) {
            Object value=args[i];
            if(microseconds&&value instanceof java.time.LocalDateTime time)
                value=time.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            statement.setObject(i+1,value);
        }
    }
}
