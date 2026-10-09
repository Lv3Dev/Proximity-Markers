package com.proximitymarkers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Deque;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class ProximityMarkersOverlay extends Overlay
{
	private static final int MAX_PROXIMITY_SIZE = 64;
	private static final Stroke TILE_STROKE = new BasicStroke(1f);

	private final Client client;
	private final ProximityMarkersPlugin plugin;
	private final ProximityMarkersConfig config;

	@Inject
	private ProximityMarkersOverlay(Client client, ProximityMarkersPlugin plugin, ProximityMarkersConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.pluginEnabled())
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return null;
		}

		List<Marker> markers = plugin.getMarkers();
		if (markers.isEmpty())
		{
			return null;
		}

		boolean hasNpc = false;
		boolean hasExplosion = false;
		boolean hasObject = false;
		boolean hasGraphicsObject = false;
		boolean hasProjectile = false;
		for (Marker marker : markers)
		{
			if (marker == null || !marker.isEnabled())
			{
				continue;
			}
			if (marker.isExplosion() && !marker.resolvedExplosionNpcName().isEmpty())
			{
				hasExplosion = true;
			}
			if (marker.getType() == null || marker.resolvedTarget().isEmpty())
			{
				continue;
			}
			switch (marker.getType())
			{
				case NPC:
					hasNpc = true;
					break;
				case OBJECT:
					hasObject = true;
					break;
				case GRAPHICS_OBJECT:
					hasGraphicsObject = true;
					break;
				case PROJECTILE:
					hasProjectile = true;
					break;
			}
		}

		if (hasNpc || hasExplosion || hasObject || hasGraphicsObject || hasProjectile)
		{
			renderWorldView(graphics, top, markers, hasNpc || hasExplosion, hasObject, hasGraphicsObject, hasProjectile);
		}

		return null;
	}

	private void renderWorldView(Graphics2D graphics, WorldView worldView, List<Marker> markers, boolean hasNpc, boolean hasObject, boolean hasGraphicsObject, boolean hasProjectile)
	{
		if (worldView == null)
		{
			return;
		}

		if (hasNpc)
		{
			for (NPC npc : worldView.npcs())
			{
				highlightNpc(graphics, npc, markers);
			}
		}

		if (hasObject)
		{
			highlightObjects(graphics, worldView, markers);
		}

		if (hasGraphicsObject)
		{
			highlightGraphicsObjects(graphics, worldView, markers);
		}

		if (hasProjectile && worldView.isTopLevel())
		{
			highlightProjectiles(graphics, markers);
		}

		for (WorldView child : worldView.worldViews())
		{
			renderWorldView(graphics, child, markers, hasNpc, hasObject, hasGraphicsObject, hasProjectile);
		}
	}

	private void highlightNpc(Graphics2D graphics, NPC npc, List<Marker> markers)
	{
		if (npc == null)
		{
			return;
		}

		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		int npcSize = 1;
		NPCComposition composition = npc.getTransformedComposition();
		if (composition == null)
		{
			composition = npc.getComposition();
		}
		if (composition != null)
		{
			npcSize = Math.max(1, composition.getSize());
		}

		for (Marker marker : markers)
		{
			if (marker == null || !marker.isEnabled())
			{
				continue;
			}
			if (marker.getType() == MarkerType.NPC && MarkerMatcher.matchesNpc(marker, npc))
			{
				drawArea(graphics, localPoint, npcSize, npcSize, marker.getProximitySize(), marker.colourValue());
			}
			if (plugin.shouldDrawExplosion(marker, npc))
			{
				drawArea(graphics, localPoint, npcSize, npcSize, marker.getExplosionProximitySize(), marker.explosionColourValue());
			}
		}
	}

	private void highlightObjects(Graphics2D graphics, WorldView worldView, List<Marker> markers)
	{
		Scene scene = worldView.getScene();
		if (scene == null)
		{
			return;
		}

		Tile[][][] tiles = scene.getTiles();
		if (tiles == null)
		{
			return;
		}

		int plane = worldView.getPlane();
		if (plane < 0 || plane >= tiles.length)
		{
			return;
		}

		Tile[][] planeTiles = tiles[plane];
		if (planeTiles == null)
		{
			return;
		}

		for (int x = 0; x < planeTiles.length; x++)
		{
			Tile[] column = planeTiles[x];
			if (column == null)
			{
				continue;
			}

			for (int y = 0; y < column.length; y++)
			{
				Tile tile = column[y];
				if (tile == null)
				{
					continue;
				}

				GameObject[] gameObjects = tile.getGameObjects();
				if (gameObjects != null)
				{
					for (GameObject gameObject : gameObjects)
					{
						if (gameObject == null)
						{
							continue;
						}
						Point min = gameObject.getSceneMinLocation();
						if (min != null && (min.getX() != x || min.getY() != y))
						{
							continue;
						}
						highlightObject(graphics, gameObject, Math.max(1, gameObject.sizeX()), Math.max(1, gameObject.sizeY()), markers);
					}
				}

				highlightObject(graphics, tile.getGroundObject(), 1, 1, markers);
				highlightObject(graphics, tile.getWallObject(), 1, 1, markers);
				highlightObject(graphics, tile.getDecorativeObject(), 1, 1, markers);
			}
		}
	}

	private void highlightObject(Graphics2D graphics, TileObject object, int sizeX, int sizeY, List<Marker> markers)
	{
		if (object == null)
		{
			return;
		}

		LocalPoint localPoint = object.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		for (Marker marker : markers)
		{
			if (marker == null || !marker.isEnabled() || marker.getType() != MarkerType.OBJECT)
			{
				continue;
			}
			if (MarkerMatcher.matchesObject(marker, object, client))
			{
				drawArea(graphics, localPoint, sizeX, sizeY, marker.getProximitySize(), marker.colourValue());
			}
		}
	}

	private void highlightGraphicsObjects(Graphics2D graphics, WorldView worldView, List<Marker> markers)
	{
		Deque<GraphicsObject> graphicsObjects = worldView.getGraphicsObjects();
		if (graphicsObjects == null)
		{
			return;
		}

		for (GraphicsObject graphicsObject : graphicsObjects)
		{
			if (graphicsObject == null || graphicsObject.finished())
			{
				continue;
			}

			LocalPoint localPoint = graphicsObject.getLocation();
			if (localPoint == null)
			{
				continue;
			}

			for (Marker marker : markers)
			{
				if (marker == null || !marker.isEnabled() || marker.getType() != MarkerType.GRAPHICS_OBJECT)
				{
					continue;
				}
				if (MarkerMatcher.matchesGraphicsObject(marker, graphicsObject))
				{
					drawArea(graphics, localPoint, 1, 1, marker.getProximitySize(), marker.colourValue());
				}
			}
		}
	}

	private void highlightProjectiles(Graphics2D graphics, List<Marker> markers)
	{
		Deque<Projectile> projectiles = client.getProjectiles();
		if (projectiles == null)
		{
			return;
		}

		for (Projectile projectile : projectiles)
		{
			if (projectile == null)
			{
				continue;
			}

			LocalPoint localPoint = projectileLocation(projectile);
			if (localPoint == null)
			{
				continue;
			}

			for (Marker marker : markers)
			{
				if (marker == null || !marker.isEnabled() || marker.getType() != MarkerType.PROJECTILE)
				{
					continue;
				}
				if (MarkerMatcher.matchesProjectile(marker, projectile))
				{
					drawArea(graphics, localPoint, 1, 1, marker.getProximitySize(), marker.colourValue());
				}
			}
		}
	}

	private LocalPoint projectileLocation(Projectile projectile)
	{
		Actor actor = projectile.getTargetActor();
		if (actor == null)
		{
			actor = projectile.getSourceActor();
		}

		WorldView worldView = actor == null ? null : actor.getWorldView();
		if (worldView == null)
		{
			worldView = client.getTopLevelWorldView();
		}
		if (worldView == null)
		{
			return null;
		}

		return new LocalPoint((int) projectile.getX(), (int) projectile.getY(), worldView);
	}

	private void drawArea(Graphics2D graphics, LocalPoint localPoint, int sizeX, int sizeY, int radius, Color colour)
	{
		if (radius < 0)
		{
			radius = 0;
		}
		else if (radius > MAX_PROXIMITY_SIZE)
		{
			radius = MAX_PROXIMITY_SIZE;
		}

		int areaX = 2 * radius + Math.max(1, sizeX);
		int areaY = 2 * radius + Math.max(1, sizeY);
		Polygon poly = Perspective.getCanvasTileAreaPoly(client, localPoint, areaX, areaY, -1, 0);
		if (poly == null)
		{
			return;
		}

		Color outline = new Color(colour.getRed(), colour.getGreen(), colour.getBlue());
		Stroke previous = graphics.getStroke();
		graphics.setColor(colour);
		graphics.fill(poly);
		graphics.setStroke(TILE_STROKE);
		graphics.setColor(outline);
		graphics.draw(poly);
		graphics.setStroke(previous);
	}
}
