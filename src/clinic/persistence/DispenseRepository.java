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
        return "CONCAT("+nameColumn+",CASE WHEN strength='' THEN '' ELSE CONCAT(' ',strength) END,"
            +"CASE WHEN formulation='' THEN '' ELSE CONCAT(' (',formulation,')') END)";
    }
    static PageResult<Medicine> medicines(Connection c,int page,int requested,String query)throws SQLException {
        int size=DispenseQueries.size(requested);
        String filter=" WHERE expiry>=? AND LOCATE(?,LOWER("+displayName("name")+"))>0";
        String grouped=" FROM medicine_batches"+filter+" GROUP BY LOWER(name),LOWER(strength),LOWER(formulation)";
        String q=DispenseQueries.query(query);
        Object[] args={LocalDate.now(),q};
        int total=Sql.query(c,"SELECT COUNT(*) FROM (SELECT LOWER(name),LOWER(strength),LOWER(formulation)"+grouped+") g",r->r.getInt(1),args).get(0);
        if(page<0||(long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        List<Medicine> rows=Sql.query(c,"SELECT MIN(name) name,MIN(strength) strength,MIN(formulation) formulation,"
            +"SUM(CAST(quantity AS BIGINT)) qty,COALESCE(MIN(CASE WHEN quantity>0 THEN expiry END),MIN(expiry)) expiry,MIN(batch_date) batch_date"
            +grouped+" ORDER BY LOWER(name),LOWER(strength),LOWER(formulation) LIMIT ? OFFSET ?",r->new Medicine(
                new MedicineProduct(r.getString("name"),r.getString("strength"),r.getString("formulation")),
                Math.toIntExact(r.getLong("qty")),r.getObject("expiry",LocalDate.class),
                r.getObject("batch_date",LocalDate.class),"Available unexpired stock"),
            args[0],args[1],size,(long)page*size);
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
                new MedicineProduct(r.getString("medicine"),r.getString("strength"),r.getString("formulation")),
                r.getString("patient_id"),r.getString("staff_id"),r.getInt("quantity"),
                r.getObject("expiry",LocalDate.class),r.getString("remarks"),
                r.getObject("dispensed_at",LocalDateTime.class),r.getString("visit_id"),
                Sql.query(c,"SELECT batch_id,quantity,expiry FROM dispensing_batch_allocations WHERE transaction_id=? ORDER BY expiry,batch_id",
                    a->new BatchAllocation(a.getString("batch_id"),a.getInt("quantity"),a.getObject("expiry",LocalDate.class)),
                    r.getString("id")),r.getString("campus_id")),args.toArray());
        return new PageResult<>(rows,total,page,size);
    }
}
