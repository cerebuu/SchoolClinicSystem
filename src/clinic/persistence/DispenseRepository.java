package clinic.persistence;
import clinic.model.*;
import clinic.service.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** SQL filtering, aggregation, counting and LIMIT/OFFSET; never loads the domain aggregate. */
final class DispenseRepository {
    private DispenseRepository() {}
    static PageResult<Medicine> medicines(Connection c,int page,int requested,String query)throws SQLException {
        int size=DispenseQueries.size(requested);
        String grouped=" FROM medicine_batches WHERE expiry>=? AND LOCATE(?,LOWER(name))>0 GROUP BY LOWER(name)";
        Object[] args={LocalDate.now(),DispenseQueries.query(query)};
        int total=Sql.query(c,"SELECT COUNT(*) FROM (SELECT LOWER(name)"+grouped+") g",r->r.getInt(1),args).get(0);
        if(page<0||(long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        List<Medicine> rows=Sql.query(c,"SELECT MIN(name) name,SUM(CAST(quantity AS BIGINT)) qty,MIN(expiry) expiry,MIN(batch_date) batch_date"
            +grouped+" ORDER BY LOWER(name) LIMIT ? OFFSET ?",r->new Medicine(r.getString("name"),Math.toIntExact(r.getLong("qty")),
                r.getObject("expiry",LocalDate.class),r.getObject("batch_date",LocalDate.class),"Available unexpired stock"),args[0],args[1],size,(long)page*size);
        return new PageResult<>(rows,total,page,size);
    }
    static PageResult<MedicineTransaction> transactions(Connection c,int page,int requested,String query,
            LocalDateTime from,LocalDateTime to)throws SQLException {
        int size=DispenseQueries.size(requested);
        String where=" FROM dispensing WHERE (LOCATE(?,LOWER(patient_id))>0 OR LOCATE(?,LOWER(medicine))>0)";
        List<Object> args=new ArrayList<>(List.of(DispenseQueries.query(query),DispenseQueries.query(query)));
        if(from!=null){where+=" AND dispensed_at>=?";args.add(from);}
        if(to!=null){where+=" AND dispensed_at<?";args.add(to);}
        int total=Sql.query(c,"SELECT COUNT(*)"+where,r->r.getInt(1),args.toArray()).get(0);
        if(page<0||(long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        args.add(size);args.add((long)page*size);
        List<MedicineTransaction> rows=Sql.query(c,"SELECT *"+where+" ORDER BY dispensed_at DESC,id LIMIT ? OFFSET ?",
            r->new MedicineTransaction(r.getString("id"),r.getString("medicine"),r.getString("patient_id"),r.getString("staff_id"),
                r.getInt("quantity"),r.getObject("expiry",LocalDate.class),r.getString("remarks"),r.getObject("dispensed_at",LocalDateTime.class)),args.toArray());
        return new PageResult<>(rows,total,page,size);
    }
}
