package net.lasertag.lasertagserver.core;

import java.util.concurrent.ThreadFactory;

public class DaemonThreadFactory implements ThreadFactory {

	private final String name;
	private int counter = 0;

	public DaemonThreadFactory(String name) {
		this.name = name;
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setDaemon(true);
		t.setName(name + (++counter));
		return t;
	}
}
