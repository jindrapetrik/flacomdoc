package com.jpexs.flash.fla.exporter;

import java.io.ByteArrayInputStream;
import java.io.File;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

/**
 * Offline schema validation for XFL documents and XML parsing for XMP packets.
 */
final class XflSchemaTestSupport {

    private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private static final Schema SCHEMA = schema();

    private XflSchemaTestSupport() {
    }

    static void validate(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
        // META-INF/metadata.xml is an XMP/RDF packet, outside the XFL schema.
        if (("adobe:ns:meta/".equals(root.getNamespaceURI()) && "xmpmeta".equals(root.getLocalName()))
                || ("http://www.w3.org/1999/02/22-rdf-syntax-ns#".equals(root.getNamespaceURI())
                && "RDF".equals(root.getLocalName()))) {
            return;
        }
        Validator validator = SCHEMA.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
    }

    private static Schema schema() {
        try {
            File source = new File(System.getProperty("flacomdoc.xflSchema", "testdata/xfl-schema/xfl.xsd"));
            SchemaFactory schemas = SchemaFactory.newInstance(XS);
            schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            // The local XFL schema imports its sibling textLayout.xsd. Network
            // schema resolution remains disabled; exported XML cannot add imports.
            schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            return schemas.newSchema(source);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

}
