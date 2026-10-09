package com.proximitymarkers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Proximity Markers",
	description = "Highlights tiles around chosen NPCs, objects, graphics objects, and projectiles within a per-marker radius",
	tags = {"highlight", "npc", "object", "graphics", "projectile", "proximity", "explosion"}
)
public class ProximityMarkersPlugin extends Plugin
{
	public static final String CONFIG_GROUP = "proximitymarkers";
	public static final String CONFIG_KEY = "markers";

	private static final Type MARKER_LIST_TYPE = new TypeToken<ArrayList<Marker>>()
	{
	}.getType();

	private static final Color[] DEFAULT_COLOURS = {
		new Color(255, 64, 64, Marker.DEFAULT_ALPHA),
		new Color(64, 200, 64, Marker.DEFAULT_ALPHA),
		new Color(64, 128, 255, Marker.DEFAULT_ALPHA),
		new Color(255, 180, 0, Marker.DEFAULT_ALPHA),
		new Color(200, 64, 255, Marker.DEFAULT_ALPHA),
		new Color(0, 220, 220, Marker.DEFAULT_ALPHA),
		new Color(255, 255, 64, Marker.DEFAULT_ALPHA)
	};

	@Getter
	private final List<MarkerFolder> folders = new CopyOnWriteArrayList<>();

	@Getter
	private final List<Marker> markers = new CopyOnWriteArrayList<>();

	private final Map<Long, Set<String>> explodingNpcMarkers = new ConcurrentHashMap<>();

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ProximityMarkersOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	@Inject
	private ColorPickerManager colorPickerManager;

	private ProximityMarkersPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		loadMarkers();
		overlayManager.add(overlay);

		panel = new ProximityMarkersPanel(this);
		panel.rebuild();

		navButton = NavigationButton.builder()
			.tooltip("Proximity Markers")
			.icon(createIcon())
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		log.debug("Proximity Markers started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
		folders.clear();
		markers.clear();
		explodingNpcMarkers.clear();
		log.debug("Proximity Markers stopped");
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		loadMarkers();
		explodingNpcMarkers.clear();
		if (panel != null)
		{
			SwingUtilities.invokeLater(panel::rebuild);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			explodingNpcMarkers.clear();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		Set<String> markerUids = new HashSet<>();
		for (Marker marker : markers)
		{
			if (isExplosionMarker(marker) && MarkerMatcher.matchesExplosionNpc(marker, npc))
			{
				markerUids.add(marker.getUid());
			}
		}
		if (!markerUids.isEmpty())
		{
			explodingNpcMarkers.put(npcKey(npc), markerUids);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (npc != null)
		{
			explodingNpcMarkers.remove(npcKey(npc));
		}
	}

	boolean shouldDrawExplosion(Marker marker, NPC npc)
	{
		if (marker == null || npc == null || !isExplosionMarker(marker))
		{
			return false;
		}

		Set<String> markerUids = explodingNpcMarkers.get(npcKey(npc));
		if (markerUids != null && marker.getUid() != null && markerUids.contains(marker.getUid()))
		{
			return true;
		}

		return MarkerMatcher.matchesExplosionNpc(marker, npc) && (npc.isDead() || npc.getHealthRatio() == 0);
	}

	private static boolean isExplosionMarker(Marker marker)
	{
		return marker != null
			&& marker.isEnabled()
			&& marker.isExplosion()
			&& !marker.resolvedExplosionNpcName().isEmpty();
	}

	private static long npcKey(NPC npc)
	{
		WorldView worldView = npc.getWorldView();
		int worldViewId = worldView == null ? -1 : worldView.getId();
		return ((long) worldViewId << 32) | (npc.getIndex() & 0xFFFFFFFFL);
	}

	@Provides
	ProximityMarkersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ProximityMarkersConfig.class);
	}

	public Marker addNewMarker()
	{
		return addNewMarker(null);
	}

	public Marker addNewMarker(MarkerFolder folder)
	{
		boolean enabled = folder == null || folder.isEnabled();
		Marker marker = Marker.create(
			"Marker " + (markers.size() + 1),
			MarkerType.NPC,
			"",
			1,
			nextDefaultColour(),
			enabled);
		if (folder != null)
		{
			marker.setFolderUid(folder.getUid());
		}
		addMarker(marker);
		return marker;
	}

	public MarkerFolder addNewFolder()
	{
		MarkerFolder folder = MarkerFolder.create("Folder " + (folders.size() + 1));
		folders.add(folder);
		saveMarkers();
		return folder;
	}

	public void addMarker(Marker marker)
	{
		markers.add(marker);
		saveMarkers();
	}

	public void removeMarker(Marker marker)
	{
		markers.remove(marker);
		saveMarkers();
	}

	public void removeFolder(MarkerFolder folder)
	{
		if (folder == null || folder.getUid() == null)
		{
			return;
		}
		folders.remove(folder);
		markers.removeIf(marker -> marker != null && folder.getUid().equals(marker.getFolderUid()));
		saveMarkers();
	}

	public void setFolderEnabled(MarkerFolder folder, boolean enabled)
	{
		if (folder == null || folder.getUid() == null)
		{
			return;
		}
		folder.setEnabled(enabled);
		for (Marker marker : markers)
		{
			if (marker != null && folder.getUid().equals(marker.getFolderUid()))
			{
				marker.setEnabled(enabled);
			}
		}
		saveMarkers();
	}

	public List<Marker> topLevelMarkers()
	{
		List<Marker> topLevel = new ArrayList<>();
		for (Marker marker : markers)
		{
			if (marker != null && (marker.getFolderUid() == null || marker.getFolderUid().isEmpty()))
			{
				topLevel.add(marker);
			}
		}
		return topLevel;
	}

	public List<Marker> markersIn(MarkerFolder folder)
	{
		List<Marker> contained = new ArrayList<>();
		if (folder == null || folder.getUid() == null)
		{
			return contained;
		}
		for (Marker marker : markers)
		{
			if (marker != null && folder.getUid().equals(marker.getFolderUid()))
			{
				contained.add(marker);
			}
		}
		return contained;
	}

	public void saveMarkers()
	{
		MarkerFile file = new MarkerFile();
		file.folders = new ArrayList<>(folders);
		file.markers = new ArrayList<>(markers);
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, gson.toJson(file));
	}

	public Color nextDefaultColour()
	{
		return DEFAULT_COLOURS[markers.size() % DEFAULT_COLOURS.length];
	}

	void loadMarkers()
	{
		folders.clear();
		markers.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}

		try
		{
			JsonElement element = gson.fromJson(json, JsonElement.class);
			if (element == null || element.isJsonNull())
			{
				return;
			}

			if (element.isJsonArray())
			{
				addLoadedMarkers(gson.fromJson(element, MARKER_LIST_TYPE));
				return;
			}

			if (!element.isJsonObject())
			{
				return;
			}

			MarkerFile file = gson.fromJson(element, MarkerFile.class);
			if (file == null)
			{
				return;
			}

			if (file.folders != null)
			{
				for (MarkerFolder folder : file.folders)
				{
					if (folder == null)
					{
						continue;
					}
					if (folder.getUid() == null || folder.getUid().isEmpty())
					{
						folder.setUid(UUID.randomUUID().toString());
					}
					if (folder.getName() == null || folder.getName().trim().isEmpty())
					{
						folder.setName("Folder");
					}
					folders.add(folder);
				}
			}
			addLoadedMarkers(file.markers);

			Set<String> folderIds = new HashSet<>();
			for (MarkerFolder folder : folders)
			{
				folderIds.add(folder.getUid());
			}
			for (Marker marker : markers)
			{
				if (marker.getFolderUid() != null && !folderIds.contains(marker.getFolderUid()))
				{
					marker.setFolderUid(null);
				}
			}
		}
		catch (JsonSyntaxException e)
		{
			log.debug("Failed to load proximity markers", e);
		}
	}

	private void addLoadedMarkers(List<Marker> loaded)
	{
		if (loaded == null)
		{
			return;
		}

		for (Marker marker : loaded)
		{
			if (marker == null || marker.getType() == null)
			{
				continue;
			}
			if (marker.getUid() == null || marker.getUid().isEmpty())
			{
				marker.setUid(UUID.randomUUID().toString());
			}
			if ((marker.getTitle() == null || marker.getTitle().isEmpty()) && marker.getName() != null)
			{
				marker.setTitle(marker.getName());
			}
			if ((marker.getTarget() == null || marker.getTarget().isEmpty()) && marker.getId() != 0)
			{
				marker.setTarget(Integer.toString(marker.getId()));
			}
			if (marker.getProximitySize() < 0)
			{
				marker.setProximitySize(0);
			}
			if (marker.getExplosionProximitySize() < 0)
			{
				marker.setExplosionProximitySize(0);
			}
			if (marker.isExplosion() && marker.resolvedExplosionNpcName().isEmpty() && !marker.resolvedTarget().isEmpty())
			{
				marker.setExplosionNpcName(marker.resolvedTarget());
				marker.setExplosionProximitySize(marker.getProximitySize());
				marker.setExplosionColourValue(marker.colourValue());
			}
			markers.add(marker);
		}
	}

	private static final class MarkerFile
	{
		private List<MarkerFolder> folders;
		private List<Marker> markers;
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(255, 200, 40, 90));
		g.fillRect(1, 1, 14, 14);
		g.setColor(new Color(255, 160, 20, 180));
		g.drawRect(3, 3, 9, 9);
		g.setColor(new Color(255, 80, 40));
		g.fillRect(6, 6, 4, 4);
		g.dispose();
		return image;
	}
}
