/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onlab.onos.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.onos.cluster.ClusterEvent;
import org.onlab.onos.cluster.ClusterService;
import org.onlab.onos.cluster.ControllerNode;
import org.onlab.onos.cluster.NodeId;
import org.onlab.onos.mastership.MastershipService;
import org.onlab.onos.net.Annotated;
import org.onlab.onos.net.Annotations;
import org.onlab.onos.net.ConnectPoint;
import org.onlab.onos.net.DefaultEdgeLink;
import org.onlab.onos.net.Device;
import org.onlab.onos.net.DeviceId;
import org.onlab.onos.net.EdgeLink;
import org.onlab.onos.net.Host;
import org.onlab.onos.net.HostId;
import org.onlab.onos.net.HostLocation;
import org.onlab.onos.net.Link;
import org.onlab.onos.net.Path;
import org.onlab.onos.net.device.DeviceEvent;
import org.onlab.onos.net.device.DeviceService;
import org.onlab.onos.net.host.HostEvent;
import org.onlab.onos.net.host.HostService;
import org.onlab.onos.net.intent.Intent;
import org.onlab.onos.net.intent.IntentService;
import org.onlab.onos.net.intent.LinkCollectionIntent;
import org.onlab.onos.net.intent.OpticalConnectivityIntent;
import org.onlab.onos.net.intent.OpticalPathIntent;
import org.onlab.onos.net.intent.PathIntent;
import org.onlab.onos.net.link.LinkEvent;
import org.onlab.onos.net.link.LinkService;
import org.onlab.onos.net.provider.ProviderId;
import org.onlab.onos.net.statistic.Load;
import org.onlab.onos.net.statistic.StatisticService;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onlab.onos.cluster.ClusterEvent.Type.INSTANCE_ADDED;
import static org.onlab.onos.cluster.ClusterEvent.Type.INSTANCE_REMOVED;
import static org.onlab.onos.cluster.ControllerNode.State.ACTIVE;
import static org.onlab.onos.net.PortNumber.portNumber;
import static org.onlab.onos.net.device.DeviceEvent.Type.DEVICE_ADDED;
import static org.onlab.onos.net.device.DeviceEvent.Type.DEVICE_REMOVED;
import static org.onlab.onos.net.host.HostEvent.Type.HOST_ADDED;
import static org.onlab.onos.net.host.HostEvent.Type.HOST_REMOVED;
import static org.onlab.onos.net.link.LinkEvent.Type.LINK_ADDED;
import static org.onlab.onos.net.link.LinkEvent.Type.LINK_REMOVED;

/**
 * Facility for creating messages bound for the topology viewer.
 */
public abstract class TopologyViewMessages {

    protected static final Logger log = LoggerFactory.getLogger(TopologyViewMessages.class);

    private static final ProviderId PID = new ProviderId("core", "org.onlab.onos.core", true);
    private static final String COMPACT = "%s/%s-%s/%s";

    protected final ServiceDirectory directory;
    protected final ClusterService clusterService;
    protected final DeviceService deviceService;
    protected final LinkService linkService;
    protected final HostService hostService;
    protected final MastershipService mastershipService;
    protected final IntentService intentService;
    protected final StatisticService statService;

    protected final ObjectMapper mapper = new ObjectMapper();

    // TODO: extract into an external & durable state; good enough for now and demo
    private static Map<String, ObjectNode> metaUi = new ConcurrentHashMap<>();

    /**
     * Creates a messaging facility for creating messages for topology viewer.
     *
     * @param directory service directory
     */
    protected TopologyViewMessages(ServiceDirectory directory) {
        this.directory = checkNotNull(directory, "Directory cannot be null");
        clusterService = directory.get(ClusterService.class);
        deviceService = directory.get(DeviceService.class);
        linkService = directory.get(LinkService.class);
        hostService = directory.get(HostService.class);
        mastershipService = directory.get(MastershipService.class);
        intentService = directory.get(IntentService.class);
        statService = directory.get(StatisticService.class);
    }

    // Retrieves the payload from the specified event.
    protected ObjectNode payload(ObjectNode event) {
        return (ObjectNode) event.path("payload");
    }

    // Returns the specified node property as a number
    protected long number(ObjectNode node, String name) {
        return node.path(name).asLong();
    }

    // Returns the specified node property as a string.
    protected String string(ObjectNode node, String name) {
        return node.path(name).asText();
    }

    // Returns the specified node property as a string.
    protected String string(ObjectNode node, String name, String defaultValue) {
        return node.path(name).asText(defaultValue);
    }

    // Returns the specified set of IP addresses as a string.
    private String ip(Set<IpAddress> ipAddresses) {
        Iterator<IpAddress> it = ipAddresses.iterator();
        return it.hasNext() ? it.next().toString() : "unknown";
    }

    // Produces JSON structure from annotations.
    private JsonNode props(Annotations annotations) {
        ObjectNode props = mapper.createObjectNode();
        for (String key : annotations.keys()) {
            props.put(key, annotations.value(key));
        }
        return props;
    }

    // Produces an informational log message event bound to the client.
    protected ObjectNode info(long id, String message) {
        return message("info", id, message);
    }

    // Produces a warning log message event bound to the client.
    protected ObjectNode warning(long id, String message) {
        return message("warning", id, message);
    }

    // Produces an error log message event bound to the client.
    protected ObjectNode error(long id, String message) {
        return message("error", id, message);
    }

    // Produces a log message event bound to the client.
    private ObjectNode message(String severity, long id, String message) {
        return envelope("message", id,
                        mapper.createObjectNode()
                                .put("severity", severity)
                                .put("message", message));
    }

    // Puts the payload into an envelope and returns it.
    protected ObjectNode envelope(String type, long sid, ObjectNode payload) {
        ObjectNode event = mapper.createObjectNode();
        event.put("event", type);
        if (sid > 0) {
            event.put("sid", sid);
        }
        event.set("payload", payload);
        return event;
    }

    // Produces a cluster instance message to the client.
    protected ObjectNode instanceMessage(ClusterEvent event) {
        ControllerNode node = event.subject();
        ObjectNode payload = mapper.createObjectNode()
                .put("id", node.id().toString())
                .put("online", clusterService.getState(node.id()) == ACTIVE)
                .put("uiAttached", event.subject().equals(clusterService.getLocalNode()));

        ArrayNode labels = mapper.createArrayNode();
        labels.add(node.id().toString());
        labels.add(node.ip().toString());

        // Add labels, props and stuff the payload into envelope.
        payload.set("labels", labels);
        addMetaUi(node.id().toString(), payload);

        String type = (event.type() == INSTANCE_ADDED) ? "addInstance" :
                ((event.type() == INSTANCE_REMOVED) ? "removeInstance" : "updateInstance");
        return envelope(type, 0, payload);
    }

    // Produces a device event message to the client.
    protected ObjectNode deviceMessage(DeviceEvent event) {
        Device device = event.subject();
        ObjectNode payload = mapper.createObjectNode()
                .put("id", device.id().toString())
                .put("type", device.type().toString().toLowerCase())
                .put("online", deviceService.isAvailable(device.id()))
                .put("master", master(device.id()));

        // Generate labels: id, chassis id, no-label, optional-name
        ArrayNode labels = mapper.createArrayNode();
        labels.add(device.id().toString());
        labels.add(device.chassisId().toString());
        labels.add(""); // compact no-label view
        labels.add(device.annotations().value("name"));

        // Add labels, props and stuff the payload into envelope.
        payload.set("labels", labels);
        payload.set("props", props(device.annotations()));
        addGeoLocation(device, payload);
        addMetaUi(device.id().toString(), payload);

        String type = (event.type() == DEVICE_ADDED) ? "addDevice" :
                ((event.type() == DEVICE_REMOVED) ? "removeDevice" : "updateDevice");
        return envelope(type, 0, payload);
    }

    // Produces a link event message to the client.
    protected ObjectNode linkMessage(LinkEvent event) {
        Link link = event.subject();
        ObjectNode payload = mapper.createObjectNode()
                .put("id", compactLinkString(link))
                .put("type", link.type().toString().toLowerCase())
                .put("online", link.state() == Link.State.ACTIVE)
                .put("linkWidth", 2)
                .put("src", link.src().deviceId().toString())
                .put("srcPort", link.src().port().toString())
                .put("dst", link.dst().deviceId().toString())
                .put("dstPort", link.dst().port().toString());
        String type = (event.type() == LINK_ADDED) ? "addLink" :
                ((event.type() == LINK_REMOVED) ? "removeLink" : "updateLink");
        return envelope(type, 0, payload);
    }

    // Produces a host event message to the client.
    protected ObjectNode hostMessage(HostEvent event) {
        Host host = event.subject();
        ObjectNode payload = mapper.createObjectNode()
                .put("id", host.id().toString())
                .put("ingress", compactLinkString(edgeLink(host, true)))
                .put("egress", compactLinkString(edgeLink(host, false)));
        payload.set("cp", hostConnect(mapper, host.location()));
        payload.set("labels", labels(mapper, ip(host.ipAddresses()),
                                     host.mac().toString()));
        payload.set("props", props(host.annotations()));
        addGeoLocation(host, payload);
        addMetaUi(host.id().toString(), payload);

        String type = (event.type() == HOST_ADDED) ? "addHost" :
                ((event.type() == HOST_REMOVED) ? "removeHost" : "updateHost");
        return envelope(type, 0, payload);
    }

    // Encodes the specified host location into a JSON object.
    private ObjectNode hostConnect(ObjectMapper mapper, HostLocation location) {
        return mapper.createObjectNode()
                .put("device", location.deviceId().toString())
                .put("port", location.port().toLong());
    }

    // Encodes the specified list of labels a JSON array.
    private ArrayNode labels(ObjectMapper mapper, String... labels) {
        ArrayNode json = mapper.createArrayNode();
        for (String label : labels) {
            json.add(label);
        }
        return json;
    }

    // Returns the name of the master node for the specified device id.
    private String master(DeviceId deviceId) {
        NodeId master = mastershipService.getMasterFor(deviceId);
        return master != null ? master.toString() : "";
    }

    // Generates an edge link from the specified host location.
    private EdgeLink edgeLink(Host host, boolean ingress) {
        return new DefaultEdgeLink(PID, new ConnectPoint(host.id(), portNumber(0)),
                                   host.location(), ingress);
    }

    // Adds meta UI information for the specified object.
    private void addMetaUi(String id, ObjectNode payload) {
        ObjectNode meta = metaUi.get(id);
        if (meta != null) {
            payload.set("metaUi", meta);
        }
    }

    // Adds a geo location JSON to the specified payload object.
    private void addGeoLocation(Annotated annotated, ObjectNode payload) {
        Annotations annotations = annotated.annotations();
        String slat = annotations.value("latitude");
        String slng = annotations.value("longitude");
        try {
            if (slat != null && slng != null && !slat.isEmpty() && !slng.isEmpty()) {
                double lat = Double.parseDouble(slat);
                double lng = Double.parseDouble(slng);
                ObjectNode loc = mapper.createObjectNode()
                        .put("type", "latlng").put("lat", lat).put("lng", lng);
                payload.set("location", loc);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid geo data latitude={}; longiture={}", slat, slng);
        }
    }

    // Updates meta UI information for the specified object.
    protected void updateMetaUi(ObjectNode event) {
        ObjectNode payload = payload(event);
        metaUi.put(string(payload, "id"), (ObjectNode) payload.path("memento"));
    }

    // Returns device details response.
    protected ObjectNode deviceDetails(DeviceId deviceId, long sid) {
        Device device = deviceService.getDevice(deviceId);
        Annotations annot = device.annotations();
        int portCount = deviceService.getPorts(deviceId).size();
        return envelope("showDetails", sid,
                        json(deviceId.toString(),
                             device.type().toString().toLowerCase(),
                             new Prop("Name", annot.value("name")),
                             new Prop("Vendor", device.manufacturer()),
                             new Prop("H/W Version", device.hwVersion()),
                             new Prop("S/W Version", device.swVersion()),
                             new Prop("Serial Number", device.serialNumber()),
                             new Separator(),
                             new Prop("Latitude", annot.value("latitude")),
                             new Prop("Longitude", annot.value("longitude")),
                             new Prop("Ports", Integer.toString(portCount)),
                             new Separator(),
                             new Prop("Master", master(deviceId))));
    }

    // Returns host details response.
    protected ObjectNode hostDetails(HostId hostId, long sid) {
        Host host = hostService.getHost(hostId);
        Annotations annot = host.annotations();
        return envelope("showDetails", sid,
                        json(hostId.toString(), "host",
                             new Prop("MAC", host.mac().toString()),
                             new Prop("IP", host.ipAddresses().toString()),
                             new Separator(),
                             new Prop("Latitude", annot.value("latitude")),
                             new Prop("Longitude", annot.value("longitude"))));
    }

    // Produces a path payload to the client.
    protected ObjectNode pathMessage(Path path, String type) {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode links = mapper.createArrayNode();
        for (Link link : path.links()) {
            links.add(compactLinkString(link));
        }

        payload.put("type", type).set("links", links);
        return payload;
    }


    // Produces JSON message to trigger traffic visualization
    protected ObjectNode trafficMessage(long sid, TrafficClass... trafficClasses) {
        ObjectNode payload = mapper.createObjectNode();
        ArrayNode paths = mapper.createArrayNode();
        payload.set("paths", paths);

        for (TrafficClass trafficClass : trafficClasses) {
            for (Intent intent : trafficClass.intents) {
                boolean isOptical = intent instanceof OpticalConnectivityIntent;
                List<Intent> installables = intentService.getInstallableIntents(intent.id());
                if (installables != null) {
                    for (Intent installable : installables) {
                        String cls = isOptical ? trafficClass.type + " optical" : trafficClass.type;
                        if (installable instanceof PathIntent) {
                            addPathTraffic(paths, cls, ((PathIntent) installable).path().links());
                        } else if (installable instanceof LinkCollectionIntent) {
                            addPathTraffic(paths, cls, ((LinkCollectionIntent) installable).links());
                        } else if (installable instanceof OpticalPathIntent) {
                            addPathTraffic(paths, cls, ((OpticalPathIntent) installable).path().links());
                        }

                    }
                }
            }
        }

        return envelope("showTraffic", sid, payload);
    }

    // Adds the link segments (path or tree) associated with the specified
    // connectivity intent
    protected void addPathTraffic(ArrayNode paths, String type, Iterable<Link> links) {
        ObjectNode pathNode = mapper.createObjectNode();
        ArrayNode linksNode = mapper.createArrayNode();

        if (links != null) {
            ArrayNode labels = mapper.createArrayNode();
            boolean hasTraffic = false;
            for (Link link : links) {
                linksNode.add(compactLinkString(link));
                Load load = statService.load(link);
                String label = "";
                if (load.rate() > 0) {
                    hasTraffic = true;
                    label = load.toString();
                }
                labels.add(label);
            }
            pathNode.put("class", hasTraffic ? type + " animated" : type);
            pathNode.put("traffic", hasTraffic);
            pathNode.set("links", linksNode);
            pathNode.set("labels", labels);
            paths.add(pathNode);
        }
    }

    // Produces compact string representation of a link.
    private static String compactLinkString(Link link) {
        return String.format(COMPACT, link.src().elementId(), link.src().port(),
                             link.dst().elementId(), link.dst().port());
    }

    // Produces JSON property details.
    private ObjectNode json(String id, String type, Prop... props) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode()
                .put("id", id).put("type", type);
        ObjectNode pnode = mapper.createObjectNode();
        ArrayNode porder = mapper.createArrayNode();
        for (Prop p : props) {
            porder.add(p.key);
            pnode.put(p.key, p.value);
        }
        result.set("propOrder", porder);
        result.set("props", pnode);
        return result;
    }

    // Auxiliary key/value carrier.
    private class Prop {
        public final String key;
        public final String value;

        protected Prop(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    // Auxiliary properties separator
    private class Separator extends Prop {
        protected Separator() {
            super("-", "");
        }
    }

    // Auxiliary carrier of data for requesting traffic message.
    protected class TrafficClass {
        public final String type;
        public final Set<Intent> intents;

        TrafficClass(String type, Set<Intent> intents) {
            this.type = type;
            this.intents = intents;
        }
    }

}
