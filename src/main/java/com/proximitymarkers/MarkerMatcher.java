package com.proximitymarkers;

import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Projectile;
import net.runelite.api.TileObject;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

final class MarkerMatcher
{
	private MarkerMatcher()
	{
	}

	static boolean matchesNpc(Marker marker, NPC npc)
	{
		return matchesNpcTarget(marker.resolvedTarget(), npc);
	}

	static boolean matchesExplosionNpc(Marker marker, NPC npc)
	{
		return matchesNpcTarget(marker.resolvedExplosionNpcName(), npc);
	}

	private static boolean matchesNpcTarget(String target, NPC npc)
	{
		if (target == null || target.isEmpty() || npc == null)
		{
			return false;
		}

		if (isInteger(target))
		{
			int id = Integer.parseInt(target);
			if (npc.getId() == id)
			{
				return true;
			}
			NPCComposition transformed = npc.getTransformedComposition();
			if (transformed != null && transformed.getId() == id)
			{
				return true;
			}
			NPCComposition composition = npc.getComposition();
			return composition != null && composition.getId() == id;
		}

		return nameMatches(target, npc.getName())
			|| nameMatches(target, compositionName(npc.getTransformedComposition()))
			|| nameMatches(target, compositionName(npc.getComposition()));
	}

	static boolean matchesObject(Marker marker, TileObject object, Client client)
	{
		String target = marker.resolvedTarget();
		if (target.isEmpty() || object == null)
		{
			return false;
		}

		int objectId = object.getId();
		if (isInteger(target))
		{
			return Integer.parseInt(target) == objectId;
		}

		ObjectComposition definition = client.getObjectDefinition(objectId);
		if (definition == null)
		{
			return false;
		}
		if (definition.getImpostorIds() != null)
		{
			ObjectComposition impostor = definition.getImpostor();
			if (impostor != null)
			{
				definition = impostor;
			}
		}
		return nameMatches(target, definition.getName());
	}

	static boolean matchesGraphicsObject(Marker marker, GraphicsObject graphicsObject)
	{
		if (graphicsObject == null)
		{
			return false;
		}
		return matchesId(marker.resolvedTarget(), graphicsObject.getId());
	}

	static boolean matchesProjectile(Marker marker, Projectile projectile)
	{
		if (projectile == null)
		{
			return false;
		}
		return matchesId(marker.resolvedTarget(), projectile.getId());
	}

	private static boolean matchesId(String target, int id)
	{
		if (target.isEmpty())
		{
			return false;
		}
		if (isInteger(target))
		{
			try
			{
				return Integer.parseInt(target) == id;
			}
			catch (NumberFormatException ex)
			{
				return false;
			}
		}
		return WildcardMatcher.matches(target, Integer.toString(id));
	}

	private static boolean nameMatches(String pattern, String name)
	{
		if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
		{
			return false;
		}
		return WildcardMatcher.matches(pattern, Text.removeTags(name));
	}

	private static String compositionName(NPCComposition composition)
	{
		return composition == null ? null : composition.getName();
	}

	private static boolean isInteger(String value)
	{
		if (value.isEmpty())
		{
			return false;
		}
		int i = 0;
		if (value.charAt(0) == '-')
		{
			if (value.length() == 1)
			{
				return false;
			}
			i = 1;
		}
		for (; i < value.length(); i++)
		{
			if (!Character.isDigit(value.charAt(i)))
			{
				return false;
			}
		}
		return true;
	}
}
