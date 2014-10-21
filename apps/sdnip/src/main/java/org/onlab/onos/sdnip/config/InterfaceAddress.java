package org.onlab.onos.sdnip.config;

import java.util.Objects;

import org.onlab.onos.net.ConnectPoint;
import org.onlab.onos.net.DeviceId;
import org.onlab.onos.net.PortNumber;
import org.onlab.packet.IpAddress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

/**
 * Represents an address of a {@link BgpSpeaker} configured on an
 * {@link Interface}.
 * <p/>
 * Each InterfaceAddress includes the interface name and an IP address.
 */
public class InterfaceAddress {
    private final ConnectPoint connectPoint;
    private final IpAddress ipAddress;

    /**
     * Creates an InterfaceAddress object.
     *
     * @param dpid the DPID of the interface as a String
     * @param port the port of the interface
     * @param ipAddress the IP address of a {@link BgpSpeaker} configured on
     * the interface
     */
    public InterfaceAddress(@JsonProperty("interfaceDpid") String dpid,
                            @JsonProperty("interfacePort") int port,
                            @JsonProperty("ipAddress") String ipAddress) {
        this.connectPoint = new ConnectPoint(
                DeviceId.deviceId(SdnIpConfigReader.dpidToUri(dpid)),
                PortNumber.portNumber(port));
        this.ipAddress = IpAddress.valueOf(ipAddress);
    }

    /**
     * Gets the connection point of the peer.
     *
     * @return the connection point
     */
    public ConnectPoint connectPoint() {
        return connectPoint;
    }

    /**
     * Gets the IP address of a BGP speaker configured on an {@link Interface}.
     *
     * @return the IP address
     */
    public IpAddress ipAddress() {
        return ipAddress;
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectPoint, ipAddress);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof InterfaceAddress)) {
            return false;
        }

        InterfaceAddress that = (InterfaceAddress) obj;
        return Objects.equals(this.connectPoint, that.connectPoint)
                && Objects.equals(this.ipAddress, that.ipAddress);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("connectPoint", connectPoint)
                .add("ipAddress", ipAddress)
                .toString();
    }
}
