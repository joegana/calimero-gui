/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2015, 2016 B. Malinowsky

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

    Linking this library statically or dynamically with other modules is
    making a combined work based on this library. Thus, the terms and
    conditions of the GNU General Public License cover the whole
    combination.

    As a special exception, the copyright holders of this library give you
    permission to link this library with independent modules to produce an
    executable, regardless of the license terms of these independent
    modules, and to copy and distribute the resulting executable under terms
    of your choice, provided that you also meet, for each linked independent
    module, the terms and conditions of the license of that module. An
    independent module is a module which is not derived from or based on
    this library. If you modify this library, you may extend this exception
    to your version of the library, but you are not obligated to do so. If
    you do not wish to do so, delete this exception statement from your
    version.
*/

package tuwien.auto.calimero.gui;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXException;
import tuwien.auto.calimero.KNXFormatException;
import tuwien.auto.calimero.dptxlator.DPT;
import tuwien.auto.calimero.dptxlator.DPTXlator;
import tuwien.auto.calimero.dptxlator.PropertyTypes;
import tuwien.auto.calimero.dptxlator.TranslatorTypes;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.mgmt.Description;
import tuwien.auto.calimero.mgmt.PropertyAccess;
import tuwien.auto.calimero.mgmt.PropertyClient;
import tuwien.auto.calimero.mgmt.PropertyClient.PropertyKey;
import tuwien.auto.calimero.tools.Property;
import tuwien.auto.calimero.xml.KNXMLException;
import tuwien.auto.calimero.xml.XmlInputFactory;
import tuwien.auto.calimero.xml.XmlReader;

/**
 * @author B. Malinowsky
 */
class PropertyEditorTab extends BaseTabLayout
{
	private static final String ObjectHeader = "Object Header";
	private static final String ObjectIndex = "Object Index";
	private static final String ObjectType = "Object Type";

	private enum Columns {
		Count,
		Pid,
		Name,
		Elements,
		Values,
		AccessLevel,
		Description
	}

	private static final List<PropertyClient.Property> definitions = new ArrayList<>();
	private static final Map<PropertyKey, PropertyClient.Property> map = new HashMap<>();

	private static final Color title = Main.display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
	private static final Color bkgnd = Main.display.getSystemColor(SWT.COLOR_LIST_SELECTION);

	private Composite editArea;
	private TableEditor editor;
	private Combo bounds;

	private final ConnectArguments connect;
	private Thread toolThread;
	private final List<String[]> commands = Collections.synchronizedList(new ArrayList<>());

	static {
		loadDefinitions();
	}

	PropertyEditorTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, (args.protocol + " connection to " + args.name),
				"Connecting" + (args.remote == null ? "" : " to " + args.remote) + " on port "
						+ args.port + (args.useNat() ? ", using NAT" : ""));
		connect = args;

		final int numberWidth = 12;
		list.setLinesVisible(true);
		final TableColumn ei = new TableColumn(list, SWT.RIGHT);
		ei.setText("Index");
		ei.setWidth(numberWidth);
		final TableColumn pid = new TableColumn(list, SWT.RIGHT);
		pid.setText("PID");
		pid.setWidth(numberWidth);
		final TableColumn name = new TableColumn(list, SWT.LEFT);
		name.setText("Name");
		name.setWidth(50);
		final TableColumn entries = new TableColumn(list, SWT.RIGHT);
		entries.setText("Elements");
		entries.setWidth(numberWidth);
		final TableColumn elems = new TableColumn(list, SWT.LEFT);
		elems.setText("Values (max. 15 Bytes)");
		elems.setWidth(60);
		final TableColumn rw = new TableColumn(list, SWT.LEFT);
		rw.setText("R/W Access");
		rw.setWidth(20);
		final TableColumn desc = new TableColumn(list, SWT.LEFT);
		desc.setText("Description");
		desc.setWidth(80);
		enableColumnAdjusting();

		addTableEditor(list);

		final Listener paintListener = (e) -> onItemPaint(e);
		list.addListener(SWT.MeasureItem, paintListener);
		list.addListener(SWT.PaintItem, paintListener);

		runProperties(Arrays.asList("scan", "all"), true);
	}

	private void addTableEditor(final Table table)
	{
		editor = new TableEditor(table);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;
		table.addListener(SWT.MouseDown, this::onMouseDown);
	}

	private void onItemPaint(final Event event)
	{
		final Table table = list;
		if (event.type == SWT.PaintItem && event.item != null) {
			if (event.item.getData(ObjectHeader) == null)
				return;
			final TableItem ti = (TableItem) event.item;
			ti.setBackground(bkgnd);

			int offset = -30;
			for (int i = 0; i < event.index; i++) {
				final TableColumn col = table.getColumn(i);
				offset += col.getWidth();
			}

			final String io = (String) event.item.getData(ObjectIndex);
			final String objType = (String) event.item.getData(ObjectType);
			final String header = "Interface Object " + io + " - "
					+ PropertyClient.getObjectTypeName(Integer.parseInt(objType)) + " (Object Type "
					+ objType + ")";
			final GC gc = new GC(table);
			final Point extent = gc.stringExtent(header);
			gc.dispose();

			event.gc.setForeground(title);
			final int y = event.y + (event.height - extent.y) / 2;
			event.gc.drawString(header, event.x - offset, y);
		}
	}

	private void onMouseDown(final Event event)
	{
		final Table table = list;
		final Rectangle clientArea = table.getClientArea();
		final Point pt = new Point(event.x, event.y);

		int index = table.getTopIndex();
		while (index < table.getItemCount()) {
			boolean visible = false;
			final TableItem item = table.getItem(index);
			final Rectangle rect = item.getBounds(Columns.Values.ordinal());
			if (item.getData(ObjectHeader) == null && rect.contains(pt)) {
				final int column = Columns.Values.ordinal();
				final Text text = new Text(table, SWT.NONE);
				final Listener textListener = new Listener() {
					public void handleEvent(final Event e)
					{
						if (e.type == SWT.FocusOut) {
							item.setText(column, text.getText());
							text.dispose();
						}
						else if (e.type == SWT.Traverse) {
							switch (e.detail) {
							case SWT.TRAVERSE_RETURN:
								item.setText(column, text.getText());
								// fall through
							case SWT.TRAVERSE_ESCAPE:
								text.dispose();
								e.doit = false;
							default:
							}
						}
					}
				};
				text.setFont(Main.font);
				text.addListener(SWT.FocusOut, textListener);
				text.addListener(SWT.Traverse, textListener);
				editor.setEditor(text, item, Columns.Values.ordinal());
				text.setText(item.getText(Columns.Values.ordinal()));
				text.selectAll();
				text.setFocus();

				updateDptBounds((String) item.getData(ObjectType),
						item.getText(Columns.Pid.ordinal()));
				return;
			}
			if (!visible && rect.intersects(clientArea))
				visible = true;
			if (!visible)
				return;
			index++;
		}
	}

	private void updateDptBounds(final String objType, final String pid)
	{
		bounds.removeAll();

		final PropertyClient.Property p = getDefinition(Integer.parseInt(objType),
				Integer.parseInt(pid));
		if (p == null)
			return;

		DPTXlator t = null;
		try {
			if (p.getDPT() != null)
				t = TranslatorTypes.createTranslator(0, p.getDPT());
		}
		catch (final KNXException | RuntimeException e) {}
		try {
			if (t == null)
				t = PropertyTypes.createTranslator(p.getPDT());
		}
		catch (final KNXException | RuntimeException e) {}
		if (t == null)
			return;

		final DPT dpt = t.getType();
		bounds.add(dpt.getLowerValue());
		bounds.add(dpt.getUpperValue());
	}

	private static void loadDefinitions()
	{
		try (final InputStream is = PropertyEditorTab.class.getResourceAsStream("/properties.xml");
				final XmlReader r = XmlInputFactory.newInstance().createXMLStreamReader(is)) {
			definitions.addAll(new PropertyClient.XmlPropertyDefinitions().load(r));
		}
		catch (final IOException | KNXMLException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		// TODO export name should contain KNX device address/name
		addResetAndExport(false, "_properties.csv");
	}

	@Override
	protected void initTableBottom(final Composite parent, final Sash sash)
	{
		editArea = new Composite(parent, SWT.NONE);
		final FormData editData = new FormData();
		editData.bottom = new FormAttachment(sash);
		editData.left = new FormAttachment(0);
		editData.right = new FormAttachment(100);
		editArea.setLayoutData(editData);
		((FormData) list.getLayoutData()).bottom = new FormAttachment(editArea);

		final RowLayout row = new RowLayout(SWT.HORIZONTAL);
		row.spacing = 10;
		row.fill = true;
		editArea.setLayout(row);

		final Button read = new Button(editArea, SWT.NONE);
		read.setText("Read");
		read.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final TableItem[] selection = list.getSelection();
				if (selection.length > 0) {
					final TableItem item = selection[0];
					final String idx = (String) item.getData(ObjectIndex);
					final String pid = item.getText(Columns.Pid.ordinal());
					final String elems = item.getText(Columns.Elements.ordinal());

					runCommand(new String[] { "get", idx, pid, "1",
						"" + Math.min(15, Integer.parseInt(elems)) });
				}
			}
		});

		final Button write = new Button(editArea, SWT.NONE);
		write.setText("Apply All");
		bounds = new Combo(editArea, SWT.DROP_DOWN);
		setFieldSize(bounds, 15);

		write.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{}
		});

		for (final Control c : editArea.getChildren())
			c.setEnabled(false);
	}

	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (toolThread != null)
			toolThread.interrupt();
	}

	private void setFieldSize(final Combo field, final int columns)
	{
		final GC gc = new GC(field);
		final FontMetrics fm = gc.getFontMetrics();
		final int width = columns * fm.getAverageCharWidth();
		final int height = fm.getHeight();
		gc.dispose();
		field.setLayoutData(new RowData(field.computeSize(width, height)));
	}

	private int count;
	private int lastObjIndex = -1;

	private void addRow(final Description d)
	{
		if (lastObjIndex != d.getObjectIndex()) {
			final String[] keys = new String[] { ObjectHeader, ObjectIndex, ObjectType, };
			final String[] data = new String[] { "", "" + d.getObjectIndex(),
				"" + d.getObjectType() };
			lastObjIndex = d.getObjectIndex();
			count = 0;
			asyncAddListItem(new String[Columns.values().length], keys, data);
		}

		final String[] keys = new String[] { ObjectIndex, ObjectType, };
		final String[] data = new String[] { "" + d.getObjectIndex(), "" + d.getObjectType() };

		final PropertyClient.Property p = getDefinition(d.getObjectType(), d.getPID());
		final String name = p != null ? p.getPIDName() : "n/a";
		final String desc = p != null ? p.getName() : "n/a";
		final String rw = d.getReadLevel() + "/" + d.getWriteLevel()
				+ (d.isWriteEnabled() ? ", w.enabled" : " (read-only)");

		final String[] item = new String[] { "" + count++, "" + d.getPID(), name,
			"" + d.getCurrentElements(), "", rw, desc };

		asyncAddListItem(item, keys, data);
	}

	private PropertyClient.Property getDefinition(final int objType, final int pid)
	{
		PropertyClient.Property p = map.get(new PropertyKey(objType, pid));
		if (p == null)
			p = map.get(new PropertyKey(PropertyKey.GLOBAL_OBJTYPE, pid));
		return p;
	}

	private void runCommand(final String[] cmd)
	{
		synchronized (commands) {
			commands.add(cmd);
			commands.notify();
		}
	}

	private void runProperties(final List<String> cmd, final boolean init)
	{
		// setup tool argument array
		final List<String> args = new ArrayList<String>();
		args.add("--verbose");
		args.addAll(connect.getArgs(true));
		args.addAll(cmd);
		asyncAddLog("Using command line: " + String.join(" ", args));

		toolThread = new Thread() {
			@Override
			public void run()
			{
				try {
					final Property tool = new Property(args.toArray(new String[args.size()])) {
						protected void runCommand(final String[] cmd) throws InterruptedException
						{
							if (init) {
								pc.addDefinitions(definitions);
								map.putAll(pc.getDefinitions());

								super.runCommand(cmd);

								final List<String> object = new ArrayList<>();
								final List<String> pid = new ArrayList<>();
								final List<String> elems = new ArrayList<>();
								Main.syncExec(new Runnable() {
									@Override
									public void run()
									{
										for (final TableItem item : list.getItems()) {
											final String text = item.getText(Columns.Pid.ordinal());
											if (text.length() > 0) {
												object.add((String) item.getData(ObjectIndex));
												pid.add(text);
												elems.add(item.getText(Columns.Elements.ordinal()));
											}
										}
									}
								});
								for (int i = 0; i < object.size(); i++) {
									try {
										final int currentElems = Integer.parseInt(elems.get(i));
										super.runCommand(new String[] { "get", object.get(i), pid.get(i), "1",
											"" + currentElems });
									}
									catch (final RuntimeException e) {
										asyncAddLog(e.toString());
									}
								}
								Main.asyncExec(new Runnable() {
									@Override
									public void run()
									{
										if (editArea.isDisposed())
											return;
										for (final Control c : editArea.getChildren())
											c.setEnabled(true);
										setHeaderInfo("Loaded properties using connection"
												+ (connect.remote == null ? ""
														: " to " + connect.remote)
												+ " on port " + connect.port
												+ (connect.useNat() ? ", using NAT" : ""));
									}
								});
							}
							while (true) {
								final String[] command;
								synchronized (commands) {
									while (commands.isEmpty())
										commands.wait();
									command = commands.remove(0);
								}
								try {
									super.runCommand(command);
								}
								catch (final RuntimeException e) {
									asyncAddLog(e.toString());
								}
							}
						}

						@Override
						protected void onDescription(final Description d)
						{
							addRow(d);
						}

						protected void onPropertyValue(final int idx, final int pid,
							final String value)
						{
							Main.asyncExec(new Runnable() {
								@Override
								public void run()
								{
									if (editArea.isDisposed())
										return;
									final TableItem item = find(idx, pid);
									if (item != null) {
										String formatted = value;
										// first attempt in formatting certain values
										// TODO refactor into generic junks
										try {
											if (pid == PropertyAccess.PID.ADDITIONAL_INDIVIDUAL_ADDRESSES
													|| pid == PropertyAccess.PID.KNX_INDIVIDUAL_ADDRESS) {
												formatted = asIndividualAddresses(value);
											}
											if (pid == PropertyAccess.PID.TABLE) {
												formatted = asGroupAddresses(value);
											}
											else if (pid == PropertyAccess.PID.FRIENDLY_NAME)
												formatted = asString(value);
											else if (pid == PropertyAccess.PID.DESCRIPTION)
												formatted = asString(value);
											else if (pid == PropertyAccess.PID.IP_ADDRESS
													|| pid == PropertyAccess.PID.CURRENT_IP_ADDRESS
													|| pid == PropertyAccess.PID.CURRENT_DEFAULT_GATEWAY
													|| pid == PropertyAccess.PID.CURRENT_SUBNET_MASK
													|| pid == PropertyAccess.PID.DEFAULT_GATEWAY
													|| pid == PropertyAccess.PID.ROUTING_MULTICAST_ADDRESS
													|| pid == PropertyAccess.PID.SYSTEM_SETUP_MULTICAST_ADDRESS)
												formatted = asIPAddress(value);
										}
										catch (final RuntimeException e) {
											asyncAddLog(e);
										}

										item.setText(Columns.Values.ordinal(), formatted);
									}
								}

								private String asString(final String value)
								{
									final StringBuilder sb = new StringBuilder();
									for (final String s : strip(value))
										sb.append((char) Integer.parseInt(s));
									return sb.toString();
								}

								private String asIndividualAddresses(final String value)
								{
									return strip(value).stream().map(s -> {
										try {
											return new IndividualAddress(s).toString();
										}
										catch (final KNXFormatException e) {
											return s;
										}
									}).collect(Collectors.joining(", "));
								}

								private String asGroupAddresses(final String value)
								{
									return strip(value).stream().map(s -> {
										try {
											return new GroupAddress(s).toString();
										}
										catch (final KNXFormatException e) {
											return s;
										}
									}).collect(Collectors.joining(", "));
								}

								private String asIPAddress(final String value)
								{
									try {
										final long l = Long.parseLong(value);
										final byte[] addr = new byte[] { (byte) (l >>> 24), (byte) (l >>> 16),
											(byte) (l >>> 8), (byte) l };
										return InetAddress.getByAddress(addr).getHostAddress();
									}
									catch (final UnknownHostException e) {
										return value;
									}
								}

								private List<String> strip(final String value)
								{
									return Arrays.asList(value.replaceAll("\\[|\\s|\\]", "").split(","));
								}
							});
						}

						protected void onCompletion(final Exception thrown, final boolean canceled)
						{
							if (thrown != null) {
								asyncAddLog(thrown.toString());
							}
						}
					};
					tool.run();
				}
				catch (final Exception e) {
					asyncAddLog(e.getMessage());
				}
			}
		};
		toolThread.start();
	}

	// assumes we're running on the main thread
	private TableItem find(final int oi, final int pid)
	{
		for (final TableItem item : list.getItems()) {
			if (item.getData(ObjectIndex).equals("" + oi)
					&& item.getText(Columns.Pid.ordinal()).equals("" + pid))
				return item;
		}
		return null;
	}
}
