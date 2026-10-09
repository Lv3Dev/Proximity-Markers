package com.proximitymarkers;

import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MarkerFolder
{
	private String uid;
	private String name;
	private boolean enabled = true;
	private boolean collapsed;

	public String displayName()
	{
		if (name != null && !name.trim().isEmpty())
		{
			return name.trim();
		}
		return "Folder";
	}

	public static MarkerFolder create(String name)
	{
		MarkerFolder folder = new MarkerFolder();
		folder.uid = UUID.randomUUID().toString();
		folder.name = name;
		folder.enabled = true;
		folder.collapsed = false;
		return folder;
	}
}
