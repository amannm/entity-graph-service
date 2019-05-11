package systems.cauldron.service.entitygraph.gateway;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryException;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.system.Txn;

import java.io.OutputStream;

public class QueryGraphGateway {

    private final String endpointUrl;

    public QueryGraphGateway(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public void query(String queryString, OutputStream outputStream) throws QueryException, IllegalArgumentException {
        Query query = QueryFactory.create(queryString);
        if (!query.isSelectType()) {
            throw new IllegalArgumentException("provided query string is not a SPARQL SELECT statement");
        }
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeRead(conn, () -> {
                conn.queryResultSet(query, qr -> {
                    ResultSetFormatter.outputAsJSON(outputStream, qr);
                });
            });
        }
    }

}
