package com.isoburn.util;

import com.isoburn.model.RemovableDrive;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlistParser {

    public List<RemovableDrive> parseDiskutilList(String plistXml) throws Exception {
        List<RemovableDrive> drives = new ArrayList<>();

        Document doc = parseXml(plistXml);
        Element root = doc.getDocumentElement();

        NodeList dictNodes = root.getElementsByTagName("dict");
        if (dictNodes.getLength() == 0) {
            return drives;
        }

        Element mainDict = (Element) dictNodes.item(0);
        Map<String, Object> rootDict = parseDict(mainDict);

        // Use AllDisks array which contains all disk identifiers
        @SuppressWarnings("unchecked")
        List<String> allDisks = (List<String>) rootDict.get("AllDisks");

        if (allDisks == null) {
            // Fallback to AllDisksAndPartitions
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allDisksAndPartitions =
                (List<Map<String, Object>>) rootDict.get("AllDisksAndPartitions");

            if (allDisksAndPartitions != null) {
                for (Map<String, Object> disk : allDisksAndPartitions) {
                    String deviceIdentifier = (String) disk.get("DeviceIdentifier");
                    if (deviceIdentifier != null) {
                        RemovableDrive drive = RemovableDrive.builder()
                                .deviceIdentifier(deviceIdentifier)
                                .build();
                        drives.add(drive);
                    }
                }
            }
            return drives;
        }

        for (String diskId : allDisks) {
            RemovableDrive drive = RemovableDrive.builder()
                    .deviceIdentifier(diskId)
                    .build();
            drives.add(drive);
        }

        return drives;
    }

    public RemovableDrive parseDiskInfo(String plistXml) throws Exception {
        Document doc = parseXml(plistXml);
        Element root = doc.getDocumentElement();

        NodeList dictNodes = root.getElementsByTagName("dict");
        if (dictNodes.getLength() == 0) {
            return null;
        }

        Element mainDict = (Element) dictNodes.item(0);
        Map<String, Object> diskInfo = parseDict(mainDict);

        String deviceIdentifier = (String) diskInfo.get("DeviceIdentifier");
        String volumeName = (String) diskInfo.get("VolumeName");
        if (volumeName == null || volumeName.isBlank()) {
            volumeName = (String) diskInfo.get("MediaName");
        }
        String mountPoint = (String) diskInfo.get("MountPoint");
        String busProtocol = (String) diskInfo.get("BusProtocol");

        // Try multiple size keys
        Long size = (Long) diskInfo.get("TotalSize");
        if (size == null) {
            size = (Long) diskInfo.get("Size");
        }
        if (size == null) {
            size = (Long) diskInfo.get("IOKitSize");
        }

        Boolean removableMedia = (Boolean) diskInfo.get("RemovableMedia");
        Boolean ejectable = (Boolean) diskInfo.get("Ejectable");
        Boolean internal = (Boolean) diskInfo.get("Internal");

        // SD cards and similar are removable even if "internal"
        boolean isRemovable = (removableMedia != null && removableMedia)
                           || (ejectable != null && ejectable)
                           || "Secure Digital".equals(busProtocol);
        boolean isExternal = (internal == null || !internal)
                          || (ejectable != null && ejectable);

        return RemovableDrive.builder()
                .deviceIdentifier(deviceIdentifier)
                .name(volumeName)
                .mountPoint(mountPoint)
                .sizeBytes(size != null ? size : 0)
                .removable(isRemovable)
                .external(isExternal)
                .busProtocol(busProtocol)
                .build();
    }

    public String parseHdiutilMountPoint(String plistXml) throws Exception {
        Document doc = parseXml(plistXml);
        Element root = doc.getDocumentElement();

        NodeList dictNodes = root.getElementsByTagName("dict");
        if (dictNodes.getLength() == 0) {
            return null;
        }

        Element mainDict = (Element) dictNodes.item(0);
        Map<String, Object> info = parseDict(mainDict);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> systemEntities =
            (List<Map<String, Object>>) info.get("system-entities");

        if (systemEntities != null) {
            for (Map<String, Object> entity : systemEntities) {
                String mountPoint = (String) entity.get("mount-point");
                if (mountPoint != null && !mountPoint.isBlank()) {
                    return mountPoint;
                }
            }
        }

        return null;
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, Object> parseDict(Element dictElement) {
        Map<String, Object> result = new HashMap<>();
        NodeList children = dictElement.getChildNodes();

        String currentKey = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;
            String tagName = element.getTagName();

            if ("key".equals(tagName)) {
                currentKey = element.getTextContent();
            } else if (currentKey != null) {
                Object value = parseValue(element);
                result.put(currentKey, value);
                currentKey = null;
            }
        }

        return result;
    }

    private Object parseValue(Element element) {
        String tagName = element.getTagName();

        return switch (tagName) {
            case "string" -> element.getTextContent();
            case "integer" -> Long.parseLong(element.getTextContent());
            case "real" -> Double.parseDouble(element.getTextContent());
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            case "dict" -> parseDict(element);
            case "array" -> parseArray(element);
            case "data" -> element.getTextContent();
            case "date" -> element.getTextContent();
            default -> element.getTextContent();
        };
    }

    private List<Object> parseArray(Element arrayElement) {
        List<Object> result = new ArrayList<>();
        NodeList children = arrayElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;
            result.add(parseValue(element));
        }

        return result;
    }
}
