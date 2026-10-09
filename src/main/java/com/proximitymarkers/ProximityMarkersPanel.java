package com.proximitymarkers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.plugins.config.ConfigPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

class ProximityMarkersPanel extends PluginPanel
{
	private static final ImageIcon EXPAND_ICON = new ImageIcon(createChevron(false));
	private static final ImageIcon COLLAPSE_ICON = new ImageIcon(createChevron(true));
	private static final ImageIcon FOLDER_ICON = new ImageIcon(createFolderIcon());
	private static final ImageIcon ON_SWITCHER;
	private static final ImageIcon OFF_SWITCHER;
	private static final Dimension PILL_SIZE;

	static
	{
		BufferedImage onSwitcher = ImageUtil.loadImageResource(ConfigPlugin.class, "switcher_on.png");
		ON_SWITCHER = new ImageIcon(onSwitcher);
		OFF_SWITCHER = new ImageIcon(ImageUtil.flipImage(
			ImageUtil.luminanceScale(ImageUtil.grayscaleImage(onSwitcher), 0.61f),
			true,
			false));
		PILL_SIZE = new Dimension(Math.max(25, ON_SWITCHER.getIconWidth()), Math.max(16, ON_SWITCHER.getIconHeight()));
	}

	private final ProximityMarkersPlugin plugin;
	private final JPanel markerView = new JPanel(new GridBagLayout());
	private final JLabel emptyLabel = new JLabel("<html><div style='width:180px;color:#C6C6C6'>Nothing here yet. + adds a marker. The folder button groups them.</div></html>");

	ProximityMarkersPanel(ProximityMarkersPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Proximity Markers");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());

		JButton addFolderButton = new JButton(FOLDER_ICON);
		addFolderButton.setToolTipText("Add folder");
		addFolderButton.setFocusable(false);
		addFolderButton.setMargin(new Insets(2, 4, 2, 4));
		addFolderButton.addActionListener(e ->
		{
			plugin.addNewFolder();
			rebuild();
		});

		JButton addButton = new JButton("+");
		addButton.setToolTipText("Add marker");
		addButton.setFocusable(false);
		addButton.setMargin(new Insets(2, 6, 2, 6));
		addButton.addActionListener(e ->
		{
			plugin.addNewMarker();
			rebuild();
		});

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		actions.setOpaque(false);
		actions.add(addFolderButton);
		actions.add(addButton);

		JPanel northPanel = new JPanel(new BorderLayout());
		northPanel.setOpaque(false);
		northPanel.setBorder(new EmptyBorder(1, 0, 10, 0));
		northPanel.add(title, BorderLayout.WEST);
		northPanel.add(actions, BorderLayout.EAST);

		markerView.setBackground(ColorScheme.DARK_GRAY_COLOR);

		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyLabel.setFont(FontManager.getRunescapeSmallFont());

		add(northPanel, BorderLayout.NORTH);
		add(markerView, BorderLayout.CENTER);
	}

	void rebuild()
	{
		markerView.removeAll();

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		List<Marker> topLevel = plugin.topLevelMarkers();
		List<MarkerFolder> folders = new ArrayList<>(plugin.getFolders());
		if (topLevel.isEmpty() && folders.isEmpty())
		{
			markerView.add(emptyLabel, constraints);
		}
		else
		{
			for (Marker marker : topLevel)
			{
				markerView.add(new MarkerRow(marker), constraints);
				constraints.gridy++;
				markerView.add(Box.createRigidArea(new Dimension(0, 8)), constraints);
				constraints.gridy++;
			}
			for (MarkerFolder folder : folders)
			{
				markerView.add(new FolderRow(folder), constraints);
				constraints.gridy++;
				markerView.add(Box.createRigidArea(new Dimension(0, 8)), constraints);
				constraints.gridy++;
			}
		}

		markerView.revalidate();
		markerView.repaint();
	}

	private class MarkerRow extends JPanel
	{
		private final Marker marker;
		private final JPanel fields;
		private final JLabel collapseButton;
		private final JLabel titleLabel;
		private final ColourSwatch swatch;
		private ColourSwatch colourRow;
		private ColourSwatch explosionColourRow;
		private JTextField explosionNpcNameField;
		private JSpinner explosionSizeSpinner;

		MarkerRow(Marker marker)
		{
			this.marker = marker;
			setLayout(new BorderLayout());
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(new EmptyBorder(6, 6, 6, 6));

			collapseButton = new JLabel(marker.isCollapsed() ? EXPAND_ICON : COLLAPSE_ICON);
			collapseButton.setToolTipText(marker.isCollapsed() ? "Expand" : "Collapse");
			collapseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			collapseButton.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					toggleCollapsed();
				}
			});

			swatch = new ColourSwatch(marker.colourValue());
			swatch.setToolTipText("Choose colour and opacity");
			swatch.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					chooseColour();
				}
			});

			titleLabel = new JLabel(marker.displayTitle());
			titleLabel.setForeground(Color.WHITE);
			titleLabel.setFont(FontManager.getRunescapeSmallFont());
			titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			titleLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					toggleCollapsed();
				}
			});

			JToggleButton enabled = new JToggleButton(OFF_SWITCHER);
			enabled.setSelectedIcon(ON_SWITCHER);
			SwingUtil.removeButtonDecorations(enabled);
			enabled.setPreferredSize(new Dimension(25, 0));
			enabled.setSelected(marker.isEnabled());
			SwingUtil.addModalTooltip(enabled, "Disable marker", "Enable marker");
			enabled.addItemListener(e ->
			{
				marker.setEnabled(enabled.isSelected());
				plugin.saveMarkers();
			});

			JButton delete = createDeleteButton(
				"Delete marker",
				() -> "Delete marker \"" + marker.displayTitle() + "\"?",
				"Delete marker",
				() -> plugin.removeMarker(marker));

			JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			headerLeft.setOpaque(false);
			headerLeft.add(collapseButton);
			headerLeft.add(swatch);
			headerLeft.add(titleLabel);

			JPanel headerRight = new JPanel(new BorderLayout(4, 0));
			headerRight.setOpaque(false);
			headerRight.add(enabled, BorderLayout.WEST);
			headerRight.add(delete, BorderLayout.EAST);

			JPanel header = new JPanel(new BorderLayout());
			header.setOpaque(false);
			header.add(headerLeft, BorderLayout.WEST);
			header.add(headerRight, BorderLayout.EAST);

			fields = buildFields();
			fields.setVisible(!marker.isCollapsed());

			add(header, BorderLayout.NORTH);
			add(fields, BorderLayout.CENTER);
		}

		private JPanel buildFields()
		{
			JPanel panel = new JPanel(new GridBagLayout());
			panel.setOpaque(false);
			panel.setBorder(new EmptyBorder(8, 4, 2, 4));

			JTextField titleField = textField(marker.displayTitle());
			onChange(titleField, () ->
			{
				String value = titleField.getText().trim();
				if (value.isEmpty())
				{
					value = "Marker";
				}
				marker.setTitle(value);
				marker.setName(value);
				titleLabel.setText(value);
				plugin.saveMarkers();
			});

			JTextField targetField = textField(marker.resolvedTarget());
			targetField.setToolTipText(targetHint(marker.getType()));
			onChange(targetField, () ->
			{
				marker.setTarget(targetField.getText().trim());
				plugin.saveMarkers();
			});

			JComboBox<MarkerType> typeBox = new JComboBox<>(MarkerType.values());
			typeBox.setSelectedItem(marker.getType() == null ? MarkerType.NPC : marker.getType());
			typeBox.addActionListener(e ->
			{
				MarkerType type = (MarkerType) typeBox.getSelectedItem();
				if (type != null)
				{
					marker.setType(type);
					targetField.setToolTipText(targetHint(type));
					plugin.saveMarkers();
				}
			});

			JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(Math.max(0, marker.getProximitySize()), 0, 64, 1));
			sizeSpinner.addChangeListener(e ->
			{
				marker.setProximitySize(((Number) sizeSpinner.getValue()).intValue());
				plugin.saveMarkers();
			});

			colourRow = new ColourSwatch(marker.colourValue());
			colourRow.setPreferredSize(new Dimension(48, 22));
			colourRow.setMinimumSize(new Dimension(48, 22));
			colourRow.setToolTipText("Click to choose colour and opacity");
			colourRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			colourRow.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					chooseColour();
				}
			});

			JToggleButton explosion = pillToggle(marker.isExplosion());
			SwingUtil.addModalTooltip(
				explosion,
				"Hide explosion options",
				"Show explosion options");

			JPanel explosionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			explosionRow.setOpaque(false);
			explosionRow.add(explosion);

			JPanel explosionFields = buildExplosionFields();
			explosionFields.setVisible(marker.isExplosion());
			explosion.addItemListener(e ->
			{
				boolean selected = explosion.isSelected();
				if (selected)
				{
					ensureExplosionDefaults();
				}
				marker.setExplosion(selected);
				explosionFields.setVisible(selected);
				plugin.saveMarkers();
				revalidate();
				repaint();
			});

			int row = 0;
			row = addField(panel, row, "Title", titleField);
			row = addField(panel, row, "Type", typeBox);
			row = addField(panel, row, "ID or name", targetField);
			row = addField(panel, row, "Proximity size", sizeSpinner);
			row = addField(panel, row, "Colour", colourRow);
			row = addField(panel, row, "Explosion", explosionRow);

			GridBagConstraints extraConstraints = new GridBagConstraints();
			extraConstraints.gridx = 0;
			extraConstraints.gridy = row;
			extraConstraints.gridwidth = 2;
			extraConstraints.weightx = 1;
			extraConstraints.fill = GridBagConstraints.HORIZONTAL;
			extraConstraints.anchor = GridBagConstraints.WEST;
			panel.add(explosionFields, extraConstraints);
			return panel;
		}

		private JPanel buildExplosionFields()
		{
			JPanel panel = new JPanel(new GridBagLayout());
			panel.setOpaque(false);
			panel.setBorder(new EmptyBorder(4, 8, 0, 0));

			explosionNpcNameField = textField(marker.resolvedExplosionNpcName());
			explosionNpcNameField.setToolTipText("NPC name to trigger the explosion radius on death. * wildcards are supported.");
			onChange(explosionNpcNameField, () ->
			{
				marker.setExplosionNpcName(explosionNpcNameField.getText().trim());
				plugin.saveMarkers();
			});

			explosionSizeSpinner = new JSpinner(new SpinnerNumberModel(Math.max(0, marker.getExplosionProximitySize()), 0, 64, 1));
			explosionSizeSpinner.addChangeListener(e ->
			{
				marker.setExplosionProximitySize(((Number) explosionSizeSpinner.getValue()).intValue());
				plugin.saveMarkers();
			});

			explosionColourRow = new ColourSwatch(marker.explosionColourValue());
			explosionColourRow.setPreferredSize(new Dimension(48, 22));
			explosionColourRow.setMinimumSize(new Dimension(48, 22));
			explosionColourRow.setToolTipText("Click to choose explosion colour and opacity");
			explosionColourRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			explosionColourRow.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					chooseExplosionColour();
				}
			});

			int row = 0;
			row = addField(panel, row, "NPC Name", explosionNpcNameField);
			row = addField(panel, row, "Proximity size", explosionSizeSpinner);
			addField(panel, row, "Colour", explosionColourRow);
			return panel;
		}

		private void ensureExplosionDefaults()
		{
			if (marker.resolvedExplosionNpcName().isEmpty() && marker.getType() == MarkerType.NPC)
			{
				marker.setExplosionNpcName(marker.resolvedTarget());
			}
			if (marker.getExplosionAlpha() == null && marker.getExplosionColour() == 0)
			{
				marker.setExplosionColourValue(marker.colourValue());
				marker.setExplosionProximitySize(marker.getProximitySize());
			}
			if (explosionNpcNameField != null)
			{
				String name = marker.resolvedExplosionNpcName();
				if (!name.equals(explosionNpcNameField.getText()))
				{
					explosionNpcNameField.setText(name);
				}
			}
			if (explosionSizeSpinner != null)
			{
				explosionSizeSpinner.setValue(Math.max(0, marker.getExplosionProximitySize()));
			}
			if (explosionColourRow != null)
			{
				explosionColourRow.setColour(marker.explosionColourValue());
			}
		}

		private void chooseColour()
		{
			RuneliteColorPicker picker = plugin.getColorPickerManager().create(
				this,
				marker.colourValue(),
				"Choose colour",
				false);
			picker.setLocationRelativeTo(this);
			picker.setOnColorChange(this::applyColour);
			picker.setOnClose(this::applyColour);
			picker.setVisible(true);
		}

		private void applyColour(Color colour)
		{
			marker.setColourValue(colour);
			swatch.setColour(colour);
			if (colourRow != null)
			{
				colourRow.setColour(colour);
			}
			plugin.saveMarkers();
		}

		private void chooseExplosionColour()
		{
			RuneliteColorPicker picker = plugin.getColorPickerManager().create(
				this,
				marker.explosionColourValue(),
				"Choose explosion colour",
				false);
			picker.setLocationRelativeTo(this);
			picker.setOnColorChange(this::applyExplosionColour);
			picker.setOnClose(this::applyExplosionColour);
			picker.setVisible(true);
		}

		private void applyExplosionColour(Color colour)
		{
			marker.setExplosionColourValue(colour);
			if (explosionColourRow != null)
			{
				explosionColourRow.setColour(colour);
			}
			plugin.saveMarkers();
		}

		private void toggleCollapsed()
		{
			marker.setCollapsed(!marker.isCollapsed());
			fields.setVisible(!marker.isCollapsed());
			collapseButton.setIcon(marker.isCollapsed() ? EXPAND_ICON : COLLAPSE_ICON);
			collapseButton.setToolTipText(marker.isCollapsed() ? "Expand" : "Collapse");
			plugin.saveMarkers();
			revalidate();
			repaint();
		}
	}

	private class FolderRow extends JPanel
	{
		private final MarkerFolder folder;
		private final JPanel body;
		private final JLabel collapseButton;
		private final JLabel titleLabel;

		FolderRow(MarkerFolder folder)
		{
			this.folder = folder;
			setLayout(new BorderLayout());
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(6, 6, 6, 6)));

			collapseButton = new JLabel(folder.isCollapsed() ? EXPAND_ICON : COLLAPSE_ICON);
			collapseButton.setToolTipText(folder.isCollapsed() ? "Expand" : "Collapse");
			collapseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			collapseButton.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					toggleCollapsed();
				}
			});

			titleLabel = new JLabel(folder.displayName());
			titleLabel.setForeground(Color.WHITE);
			titleLabel.setFont(FontManager.getRunescapeSmallFont());
			titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			titleLabel.setBorder(new EmptyBorder(0, 6, 0, 6));
			titleLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					toggleCollapsed();
				}
			});

			JToggleButton enabled = new JToggleButton(OFF_SWITCHER);
			enabled.setSelectedIcon(ON_SWITCHER);
			SwingUtil.removeButtonDecorations(enabled);
			enabled.setPreferredSize(new Dimension(25, 0));
			enabled.setSelected(folder.isEnabled());
			SwingUtil.addModalTooltip(enabled, "Disable everything in this folder", "Enable everything in this folder");
			enabled.addItemListener(e ->
			{
				boolean selected = enabled.isSelected();
				if (folder.isEnabled() == selected)
				{
					return;
				}
				plugin.setFolderEnabled(folder, selected);
				SwingUtilities.invokeLater(ProximityMarkersPanel.this::rebuild);
			});

			JButton delete = createDeleteButton(
				"Delete folder",
				() ->
				{
					int contained = plugin.markersIn(folder).size();
					if (contained == 0)
					{
						return "Delete folder \"" + folder.displayName() + "\"?";
					}
					String markers = contained == 1 ? "marker" : "markers";
					return "Delete folder \"" + folder.displayName() + "\" and the " + contained + " " + markers + " inside it?";
				},
				"Delete folder",
				() -> plugin.removeFolder(folder));

			JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			headerLeft.setOpaque(false);
			headerLeft.add(collapseButton);

			JPanel headerRight = new JPanel(new BorderLayout(4, 0));
			headerRight.setOpaque(false);
			headerRight.add(enabled, BorderLayout.WEST);
			headerRight.add(delete, BorderLayout.EAST);

			JPanel header = new JPanel(new BorderLayout());
			header.setOpaque(false);
			header.add(headerLeft, BorderLayout.WEST);
			header.add(titleLabel, BorderLayout.CENTER);
			header.add(headerRight, BorderLayout.EAST);

			body = buildBody();
			body.setVisible(!folder.isCollapsed());

			add(header, BorderLayout.NORTH);
			add(body, BorderLayout.CENTER);
		}

		private JPanel buildBody()
		{
			JPanel panel = new JPanel(new GridBagLayout());
			panel.setOpaque(false);
			panel.setBorder(new EmptyBorder(8, 4, 2, 4));

			JTextField nameField = textField(folder.displayName());
			onChange(nameField, () ->
			{
				String value = nameField.getText().trim();
				if (value.isEmpty())
				{
					value = "Folder";
				}
				folder.setName(value);
				titleLabel.setText(value);
				plugin.saveMarkers();
			});

			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridx = 0;
			constraints.gridy = 0;
			constraints.weightx = 1;
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.anchor = GridBagConstraints.WEST;

			JLabel nameLabel = new JLabel("Name");
			nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			nameLabel.setFont(FontManager.getRunescapeSmallFont());
			panel.add(nameLabel, constraints);

			constraints.gridy++;
			constraints.insets = new Insets(2, 0, 8, 0);
			panel.add(nameField, constraints);

			List<Marker> contained = plugin.markersIn(folder);
			constraints.insets = new Insets(0, 0, 0, 0);
			if (contained.isEmpty())
			{
				constraints.gridy++;
				JLabel none = new JLabel("No markers in this folder.");
				none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				none.setFont(FontManager.getRunescapeSmallFont());
				panel.add(none, constraints);
			}
			else
			{
				for (Marker marker : contained)
				{
					constraints.gridy++;
					constraints.insets = new Insets(0, 0, 6, 0);
					panel.add(new MarkerRow(marker), constraints);
				}
			}

			JButton add = new JButton("+ Add marker");
			add.setToolTipText("Add a marker to this folder");
			add.setFocusable(false);
			add.addActionListener(e ->
			{
				plugin.addNewMarker(folder);
				rebuild();
			});
			constraints.gridy++;
			constraints.insets = new Insets(2, 0, 0, 0);
			panel.add(add, constraints);
			return panel;
		}

		private void toggleCollapsed()
		{
			folder.setCollapsed(!folder.isCollapsed());
			body.setVisible(!folder.isCollapsed());
			collapseButton.setIcon(folder.isCollapsed() ? EXPAND_ICON : COLLAPSE_ICON);
			collapseButton.setToolTipText(folder.isCollapsed() ? "Expand" : "Collapse");
			plugin.saveMarkers();
			revalidate();
			repaint();
		}
	}

	private JButton createDeleteButton(String tooltip, Supplier<String> message, String title, Runnable onDelete)
	{
		JButton delete = new JButton("\u00d7");
		delete.setFont(FontManager.getRunescapeBoldFont());
		delete.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		delete.setToolTipText(tooltip);
		delete.setFocusable(false);
		delete.setMargin(new Insets(0, 0, 0, 0));
		delete.setPreferredSize(new Dimension(20, 0));
		delete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		SwingUtil.removeButtonDecorations(delete);
		delete.putClientProperty("FlatLaf.style", "border: 0,0,0,0,#0000");
		delete.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				delete.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				delete.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		delete.addActionListener(e ->
		{
			int result = JOptionPane.showOptionDialog(
				ProximityMarkersPanel.this,
				message.get(),
				title,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				new String[]{"Yes", "No"},
				"No");
			if (result == JOptionPane.YES_OPTION)
			{
				onDelete.run();
				rebuild();
			}
		});
		return delete;
	}

	private static String targetHint(MarkerType type)
	{
		if (type == MarkerType.GRAPHICS_OBJECT)
		{
			return "Graphics object ID. * wildcards are supported.";
		}
		if (type == MarkerType.PROJECTILE)
		{
			return "Projectile ID. * wildcards are supported.";
		}
		if (type == MarkerType.OBJECT)
		{
			return "Object ID or name. Names support * wildcards.";
		}
		return "NPC ID or name. Names support * wildcards.";
	}

	private static int addField(JPanel panel, int row, String label, java.awt.Component field)
	{
		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = row;
		labelConstraints.anchor = GridBagConstraints.WEST;
		labelConstraints.insets = new Insets(2, 0, 2, 6);

		JLabel jLabel = new JLabel(label);
		jLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		jLabel.setFont(FontManager.getRunescapeSmallFont());
		panel.add(jLabel, labelConstraints);

		GridBagConstraints fieldConstraints = new GridBagConstraints();
		fieldConstraints.gridx = 1;
		fieldConstraints.gridy = row;
		fieldConstraints.weightx = 1;
		fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
		fieldConstraints.insets = new Insets(2, 0, 2, 0);
		panel.add(field, fieldConstraints);
		return row + 1;
	}

	private static void onChange(JTextField field, Runnable action)
	{
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				action.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				action.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				action.run();
			}
		});
	}

	private static JToggleButton pillToggle(boolean selected)
	{
		JToggleButton toggle = new JToggleButton(OFF_SWITCHER);
		toggle.setSelectedIcon(ON_SWITCHER);
		SwingUtil.removeButtonDecorations(toggle);
		toggle.setPreferredSize(PILL_SIZE);
		toggle.setMinimumSize(PILL_SIZE);
		toggle.setMaximumSize(PILL_SIZE);
		toggle.setSelected(selected);
		toggle.setFocusable(false);
		return toggle;
	}

	private static JTextField textField(String value)
	{
		JTextField field = new JTextField(value);
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(2, 4, 2, 4)));
		return field;
	}

	private static final class ColourSwatch extends JPanel
	{
		private static final Color CHECKER_LIGHT = new Color(200, 200, 200);
		private static final Color CHECKER_DARK = new Color(150, 150, 150);
		private static final int CHECKER_SIZE = 4;

		private Color colour;

		private ColourSwatch(Color colour)
		{
			this.colour = colour;
			setPreferredSize(new Dimension(16, 16));
			setMinimumSize(new Dimension(16, 16));
			setOpaque(true);
			setBorder(BorderFactory.createLineBorder(Color.WHITE));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		private void setColour(Color colour)
		{
			this.colour = colour;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			int width = getWidth();
			int height = getHeight();
			for (int y = 0; y < height; y += CHECKER_SIZE)
			{
				for (int x = 0; x < width; x += CHECKER_SIZE)
				{
					boolean light = ((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0;
					g.setColor(light ? CHECKER_LIGHT : CHECKER_DARK);
					g.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
				}
			}
			if (colour != null)
			{
				g.setColor(colour);
				g.fillRect(0, 0, width, height);
			}
		}
	}

	private static BufferedImage createChevron(boolean expanded)
	{
		BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(180, 180, 180));
		if (expanded)
		{
			g.fillPolygon(new int[]{2, 10, 6}, new int[]{4, 4, 10}, 3);
		}
		else
		{
			g.fillPolygon(new int[]{4, 10, 4}, new int[]{2, 6, 10}, 3);
		}
		g.dispose();
		return image;
	}

	private static BufferedImage createFolderIcon()
	{
		BufferedImage image = new BufferedImage(16, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(214, 176, 86));
		g.fillRect(1, 2, 7, 3);
		g.fillRect(1, 4, 14, 9);
		g.setColor(new Color(120, 90, 30));
		g.drawRect(1, 4, 13, 8);
		g.dispose();
		return image;
	}
}
