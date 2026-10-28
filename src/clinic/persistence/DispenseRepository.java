package clinic.persistence;
import clinic.model.*;
import clinic.service.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** SQL filtering, aggregation, counting and LIMIT/OFFSET; never loads the domain aggregate. */
final class DispenseRepository {
    private DispenseRepository() {}
    private static String displayName(String nameColumn) {
        return nameColumn;
    }
    static PageResult<Medicine> medicines(Connection c,int page,int requested,String query)throws SQLException {
        int size=DispenseQueries.size(requested);
        String filter=" WHERE LOCATE(?,LOWER(CONCAT(name,' ',atc_primary,' ',atc_secondary)))>0";
        String grouped=" FROM medicine_batches"+filter+" GROUP BY LOWER(TRIM(name))";
        String q=DispenseQueries.query(query);
        Object[] args={q};
        int total=Sql.query(c,"SELECT COUNT(*) FROM (SELECT LOWER(TRIM(name))"+grouped+") g",r->r.getInt(1),args).get(0);
        if(page<0||(long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        List<Medicine> rows=Sql.query(c,"SELECT MIN(name) name,MAX(atc_primary) atc_primary,MAX(atc_secondary) atc_secondary,"
            +"SUM(CASE WHEN expiry>=? THEN quantity ELSE 0 END) qty,"
            +"COALESCE(MIN(CASE WHEN expiry>=? AND quantity>0 THEN expiry END),MIN(expiry)) expiry,MIN(batch_date) batch_date"
            +grouped+" ORDER BY LOWER(TRIM(name)) LIMIT ? OFFSET ?",r->new Medicine(
                new MedicineProduct(r.getString("name"),r.getString("atc_primary"),r.getString("atc_secondary")),
                Math.toIntExact(r.getLong("qty")),r.getObject("expiry",LocalDate.class),
                r.getObject("batch_date",LocalDate.class),"Available unexpired stock"),
            LocalDate.now(),LocalDate.now(),args[0],size,(long)page*size);
        return new PageResult<>(rows,total,page,size);
    }
    static PageResult<MedicineTransaction> transactions(Connection c,int page,int requested,String query,
            LocalDateTime from,LocalDateTime to)throws SQLException {
        return transactions(c,page,requested,query,from,to,null);
    }
    static PageResult<MedicineTransaction> transactions(Connection c,int page,int requested,String query,
            LocalDateTime from,LocalDateTime to,String campusId)throws SQLException {
        int size=DispenseQueries.size(requested);
        if(page<0)throw new IllegalArgumentException("Invalid page.");
        String where=" FROM dispensing WHERE (LOCATE(?,LOWER(patient_id))>0 OR LOCATE(?,LOWER("+displayName("medicine")+"))>0)";
        List<Object> args=new ArrayList<>(List.of(DispenseQueries.query(query),DispenseQueries.query(query)));
        if(from!=null){where+=" AND dispensed_at>=?";args.add(from);}
        if(to!=null){where+=" AND dispensed_at<?";args.add(to);}
        if(campusId!=null){where+=" AND campus_id=?";args.add(campusId);}
        int total=Sql.query(c,"SELECT COUNT(*)"+where,r->r.getInt(1),args.toArray()).get(0);
        if(page<0||(long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        args.add(size);args.add((long)page*size);
        List<MedicineTransaction> rows=Sql.query(c,"SELECT *"+where+" ORDER BY dispensed_at DESC,id LIMIT ? OFFSET ?",
            r->new MedicineTransaction(r.getString("id"),
                new MedicineProduct(r.getString("medicine"),r.getString("atc_primary"),r.getString("atc_secondary")),
                r.getString("patient_id"),r.getString("staff_id"),r.getInt("quantity"),
                r.getObject("expiry",LocalDate.class),r.getString("remarks"),
                r.getObject("dispensed_at",LocalDateTime.class),r.getString("visit_id"),
                Sql.query(c,"SELECT batch_id,quantity,expiry FROM dispensing_batch_allocations WHERE transaction_id=? ORDER BY expiry,batch_id",
                    a->new BatchAllocation(a.getString("batch_id"),a.getInt("quantity"),a.getObject("expiry",LocalDate.class)),
                    r.getString("id")),r.getString("campus_id")),args.toArray());
        return new PageResult<>(rows,total,page,size);
    }
}
