package clinic.persistence;

import java.sql.*;
import java.util.*;

final class Sql {
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
        for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
    }
}
