package clinic.persistence;

import java.sql.*;

/** Catalog-scoped metadata makes MySQL DDL retryable without IF NOT EXISTS extensions. */
final class MySqlMetadata {
    private MySqlMetadata() {}
    static boolean column(Connection c,String table,String column) throws SQLException {
        return !Sql.query(c,"SELECT COLUMN_NAME FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
            r->r.getString(1),table,column).isEmpty();
    }
    static void add(Connection c,String table,String column,String definition) throws SQLException {
        if(!column(c,table,column))execute(c,"ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
    }
    static void index(Connection c,String table,String name,String columns) throws SQLException {
        if(Sql.query(c,"SELECT INDEX_NAME FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=?",
                r->r.getString(1),table,name).isEmpty())
            execute(c,"CREATE INDEX "+name+" ON "+table+"("+columns+")");
    }
    static void require(Connection c,String table,String columns) throws SQLException {
        try(Statement s=c.createStatement()) { s.executeQuery("SELECT "+columns+" FROM "+table+" WHERE 1=0").close(); }
        catch(SQLException failure) { throw new SchemaException("Existing schema is incomplete: check table "+table+" and columns "+columns+". No base tables are created or seeded."); }
    }
    /** Foreign-key columns must preserve the parent's width, character set and collation. */
    static String idType(Connection c,String table) throws SQLException {
        var types=Sql.query(c,"SELECT COLUMN_TYPE,CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='id'",
            r->new String[]{r.getString(1),r.getString(2),r.getString(3)},table);
        if(types.size()!=1)throw new SchemaException("Missing ID column in "+table+".");
        String[] type=types.get(0);
        if(!type[0].matches("(?i)varchar\\([0-9]+\\)")||type[1]==null||!type[1].matches("[a-zA-Z0-9_]+")
                ||type[2]==null||!type[2].matches("[a-zA-Z0-9_]+"))
            throw new SchemaException("Expected a VARCHAR ID with a character set and collation in "+table+".");
        return type[0]+" CHARACTER SET "+type[1]+" COLLATE "+type[2];
    }
    static void execute(Connection c,String sql) throws SQLException {
        try(Statement s=c.createStatement()) { s.execute(sql); }
    }
}
