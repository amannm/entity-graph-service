package systems.cauldron.service.entitygraph.gateway.util;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;

import javax.json.JsonObject;

public class EntityModelBuilder {

    private final String baseUri;

    private final JsonObject source;

    private final Model model;
    private final Resource subject;

    public EntityModelBuilder(String baseUri, String entityType, String entityId, JsonObject source) {
        this.baseUri = baseUri;
        this.source = source;
        this.model = ModelFactory.createDefaultModel();
        this.subject = model.createResource(baseUri + entityType + "s/" + entityId);
        model.add(subject, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "type"), baseUri + entityType);
    }

    public EntityModelBuilder addStringProperty(String propertyName) {
        model.add(subject,
                model.createProperty(baseUri, propertyName),
                model.createTypedLiteral(source.getString(propertyName), XSDDatatype.XSDstring)
        );
        return this;
    }

    public EntityModelBuilder addIntegerProperty(String propertyName) {
        model.add(subject,
                model.createProperty(baseUri, propertyName),
                model.createTypedLiteral(source.getInt(propertyName), XSDDatatype.XSDinteger)
        );
        return this;
    }

    public EntityModelBuilder addTimestampProperty(String propertyName) {
        model.add(subject,
                model.createProperty(baseUri, propertyName),
                model.createTypedLiteral(source.getString(propertyName), XSDDatatype.XSDdateTimeStamp)
        );
        return this;
    }

    public EntityModelBuilder addObjectProperty(String propertyName, String objectType) {
        model.add(subject,
                model.createProperty(baseUri, propertyName),
                model.createResource(baseUri + objectType + "s/" + source.getString(propertyName))
        );
        return this;
    }

    public Model build() {
        return model;
    }
}
