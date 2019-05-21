package systems.cauldron.service.entitygraph.gateway.util;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import systems.cauldron.service.entitygraph.resource.EntityResourceFactory;

import javax.json.JsonObject;

import static systems.cauldron.service.entitygraph.gateway.EntityGraphGateway.NAMESPACE_PREFIX;

public class EntityModelBuilder {

    private final JsonObject source;

    private final Model model;
    private final Resource subject;

    public EntityModelBuilder(String entityType, String entityId, JsonObject source) {
        this.source = source;
        this.model = ModelFactory.createDefaultModel();
        String entityTypeUri = NAMESPACE_PREFIX + entityType;
        this.subject = model.createResource(EntityResourceFactory.getEntityPath(entityType, entityId));
        model.add(subject,
                model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "type"),
                model.createResource(entityTypeUri));
    }

    public EntityModelBuilder addStringProperty(String propertyName) {
        if (source.containsKey(propertyName)) {
            model.add(subject,
                    model.createProperty(NAMESPACE_PREFIX, propertyName),
                    model.createTypedLiteral(source.getString(propertyName), XSDDatatype.XSDstring)
            );
        }
        return this;
    }

    public EntityModelBuilder addIntegerProperty(String propertyName) {
        if (source.containsKey(propertyName)) {
            model.add(subject,
                    model.createProperty(NAMESPACE_PREFIX, propertyName),
                    model.createTypedLiteral(source.getInt(propertyName), XSDDatatype.XSDinteger)
            );
        }
        return this;
    }

    public EntityModelBuilder addTimestampProperty(String propertyName) {
        if (source.containsKey(propertyName)) {
            model.add(subject,
                    model.createProperty(NAMESPACE_PREFIX, propertyName),
                    model.createTypedLiteral(source.getString(propertyName), XSDDatatype.XSDdateTimeStamp)
            );
        }
        return this;
    }

    public EntityModelBuilder addObjectProperty(String propertyName, String objectType) {
        if (source.containsKey(propertyName)) {
            model.add(subject,
                    model.createProperty(NAMESPACE_PREFIX, propertyName),
                    model.createResource(EntityResourceFactory.getEntityPath(objectType, source.getString(propertyName)))
            );
        }
        return this;
    }

    public Model build() {
        return model;
    }
}
