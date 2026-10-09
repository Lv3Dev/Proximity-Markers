package com.proximitymarkers;

import java.awt.Color;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Marker
{
	static final int DEFAULT_ALPHA = 50;

	private String uid;
	private String title;
	private String name;
	private MarkerType type;
	private String target;
	private int id;
	private int proximitySize = 1;
	private int colour;
	private Integer alpha;
	private boolean enabled = true;
	private boolean explosion;
	private String explosionNpcName;
	private int explosionProximitySize = 1;
	private int explosionColour;
	private Integer explosionAlpha;
	private boolean collapsed;
	private String folderUid;

	public String displayTitle()
	{
		if (title != null && !title.trim().isEmpty())
		{
			return title.trim();
		}
		if (name != null && !name.trim().isEmpty())
		{
			return name.trim();
		}
		return "Marker";
	}

	public String resolvedTarget()
	{
		if (target != null && !target.trim().isEmpty())
		{
			return target.trim();
		}
		if (id != 0)
		{
			return Integer.toString(id);
		}
		return "";
	}

	public Color colourValue()
	{
		int rgb = colour & 0xFFFFFF;
		int opacity = alpha == null ? DEFAULT_ALPHA : clampAlpha(alpha);
		return new Color((opacity << 24) | rgb, true);
	}

	public void setColourValue(Color color)
	{
		this.colour = color.getRGB() & 0xFFFFFF;
		this.alpha = color.getAlpha();
	}

	public String resolvedExplosionNpcName()
	{
		return explosionNpcName == null ? "" : explosionNpcName.trim();
	}

	public Color explosionColourValue()
	{
		int rgb = explosionColour & 0xFFFFFF;
		int opacity = explosionAlpha == null ? DEFAULT_ALPHA : clampAlpha(explosionAlpha);
		return new Color((opacity << 24) | rgb, true);
	}

	public void setExplosionColourValue(Color color)
	{
		this.explosionColour = color.getRGB() & 0xFFFFFF;
		this.explosionAlpha = color.getAlpha();
	}

	private static int clampAlpha(int value)
	{
		if (value < 0)
		{
			return 0;
		}
		if (value > 255)
		{
			return 255;
		}
		return value;
	}

	public static Marker create(String title, MarkerType type, String target, int proximitySize, Color colour, boolean enabled)
	{
		Marker marker = new Marker();
		marker.uid = UUID.randomUUID().toString();
		marker.title = title;
		marker.name = title;
		marker.type = type;
		marker.target = target;
		marker.proximitySize = proximitySize;
		marker.setColourValue(colour);
		marker.enabled = enabled;
		marker.collapsed = false;
		return marker;
	}
}
