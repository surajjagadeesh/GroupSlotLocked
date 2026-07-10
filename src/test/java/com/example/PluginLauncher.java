package com.example;

import com.gsl.GroupSlotLockedPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GroupSlotLockedPlugin.class);
		RuneLite.main(args);
	}
}