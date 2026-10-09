package com.proximitymarkers;

public enum MarkerType
{
	NPC("NPC"),
	OBJECT("Object"),
	GRAPHICS_OBJECT("Graphics object"),
	PROJECTILE("Projectile");

	private final String label;

	MarkerType(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
