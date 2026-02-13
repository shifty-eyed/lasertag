package net.lasertag.lasertagserver;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LanIpUtils {

	private LanIpUtils() {
		// utility class
	}

	public static Optional<String> findLanIp() {
		List<InetAddress> candidates = findCandidateIpv4Addresses();

		// Prefer 192.168.x.x
		for (InetAddress addr : candidates) {
			if (addr.getHostAddress().startsWith("192.168.")) {
				return Optional.of(addr.getHostAddress());
			}
		}

		if (!candidates.isEmpty()) {
			return Optional.of(candidates.get(0).getHostAddress());
		}

		return Optional.empty();
	}

	private static List<InetAddress> findCandidateIpv4Addresses() {
		List<InetAddress> result = new ArrayList<>();

		Enumeration<NetworkInterface> interfaces;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
		}
		catch (SocketException e) {
			return Collections.emptyList();
		}

		if (interfaces == null) {
			return Collections.emptyList();
		}

		while (interfaces.hasMoreElements()) {
			NetworkInterface nif = interfaces.nextElement();
			try {
				if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
					continue;
				}
			}
			catch (SocketException e) {
				continue;
			}

			Enumeration<InetAddress> addresses = nif.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress addr = addresses.nextElement();
				if (addr instanceof Inet4Address
					&& !addr.isLoopbackAddress()
					&& addr.isSiteLocalAddress()) {
					result.add(addr);
				}
			}
		}

		return result;
	}
}


