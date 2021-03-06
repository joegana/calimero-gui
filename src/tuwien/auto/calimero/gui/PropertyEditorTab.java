/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2015, 2017 B. Malinowsky

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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import tuwien.auto.calimero.DataUnitBuilder;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXException;
import tuwien.auto.calimero.KNXFormatException;
import tuwien.auto.calimero.KNXTimeoutException;
import tuwien.auto.calimero.dptxlator.DPT;
import tuwien.auto.calimero.dptxlator.DPTXlator;
import tuwien.auto.calimero.dptxlator.PropertyTypes;
import tuwien.auto.calimero.dptxlator.PropertyTypes.DPTID;
import tuwien.auto.calimero.dptxlator.TranslatorTypes;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.link.KNXNetworkLink;
import tuwien.auto.calimero.mgmt.Description;
import tuwien.auto.calimero.mgmt.Destination;
import tuwien.auto.calimero.mgmt.ManagementClientImpl;
import tuwien.auto.calimero.mgmt.PropertyAccess.PID;
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
	private static final PropertyClient.Property unknown = new PropertyClient.Property(-1, "[Unknown Property]", "n/a",
			-1, -1, null);

	private static final Color title = Main.display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
	private static final Color bkgnd = Main.display.getSystemColor(SWT.COLOR_LIST_SELECTION);

	private Composite editArea;
	private SashForm sashForm;
	private Tree tree;
	private Composite detailPane;
	private Composite propertyPage;
	private TableEditor editor;
	private Combo bounds;

	private final ConnectArguments connect;
	private Thread toolThread;
	private KNXNetworkLink toolLink;
	private final List<String[]> commands = Collections.synchronizedList(new ArrayList<>());

	private final List<Description> descriptions = new ArrayList<>();
	private final Map<Description, String> values = new HashMap<>();
	private int count;

	private static final String showTree = "Use tree view for interface objects and properties";
	private static final String hideTree = "Use table view for interface objects and properties";

	static {
		loadDefinitions();
	}

	PropertyEditorTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, (args.protocol + " connection to " + args.name),
				"Connecting" + (args.remote == null ? "" : " to " + args.remote) + " on port "
						+ args.port + (args.useNat() ? ", using NAT" : ""));
		connect = args;
		final String device = connect.knxAddress.isEmpty() ? connect.remote : connect.knxAddress;
		final String prefix = "knx-properties_" + device + "_";
		final String suffix = ".csv";
		setExportName(prefix, suffix);

		final int numberWidth = 15;
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
		elems.setText("Values");
		elems.setWidth(60);
		final TableColumn rw = new TableColumn(list, SWT.LEFT);
		rw.setText("R/W Level");
		rw.setWidth(25);
		final TableColumn desc = new TableColumn(list, SWT.LEFT);
		desc.setText("Description");
		desc.setWidth(80);
		enableColumnAdjusting();
		list.addSelectionListener(adapt(this::updateDptBounds));

		addTreeView();
		addTableEditor(list);

		final Listener paintListener = (e) -> onItemPaint(e);
		list.addListener(SWT.MeasureItem, paintListener);
		list.addListener(SWT.PaintItem, paintListener);

		workArea.layout(true, true);

		final ToolTip tip = new ToolTip(Main.shell, SWT.BALLOON | SWT.ICON_WARNING);
		tip.setText("KNX property editor");
		tip.setMessage("This editor allows modification of KNX device properties.\nUse with care!");
		tip.setLocation(Main.display.map(list, null, 40, 20));
		tip.setVisible(true);
		tip.setAutoHide(true);

		runProperties(Arrays.asList("scan", "all"), true);
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		addResetAndExport(false, ".csv");
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
		row.center = true;
		editArea.setLayout(row);

		addTreeViewOption();

		Label spacer = new Label(editArea, SWT.NONE);
		RowData data = new RowData();
		data.height = 20;
		data.width = 150;
		spacer.setLayoutData(data);

		final Button read = new Button(editArea, SWT.NONE);
		read.setText("Read");
		read.addSelectionListener(adapt(e -> {
			if (list.isVisible() && list.getSelection().length > 0) {
				final TableItem item = list.getSelection()[0];
				final String idx = (String) item.getData(ObjectIndex);
				final String pid = item.getText(Columns.Pid.ordinal());
				final String elems = item.getText(Columns.Elements.ordinal());
				runCommand("get", idx, pid, "1", elems);
			}
			else if (tree.isVisible() && tree.getSelection().length > 0) {
				final TreeItem item = tree.getSelection()[0];
				// we currently don't read whole interface objects
				if (item.getParentItem() == null)
					return;
				final int objectIndex = (Integer) item.getData(ObjectIndex);
				final int pid = (Integer) item.getData(Columns.Pid.name());
				final int elems = findDescription(objectIndex, pid).getCurrentElements();
				runCommand("get", objectIndex, pid, "1", elems);
			}
		}));

		final Label range = new Label(editArea, SWT.NONE);
		range.setText("Value range:");

		bounds = new Combo(editArea, SWT.DROP_DOWN | SWT.READ_ONLY);
		setFieldSize(bounds, 15);

		spacer = new Label(editArea, SWT.SEPARATOR);
		data = new RowData();
		data.height = read.getSize().y;
		data.width = 150;
		spacer.setLayoutData(data);

		final Button restart = new Button(editArea, SWT.NONE);
		restart.setText("Restart KNX device");
		restart.addSelectionListener(adapt(e -> restart()));

		for (final Control c : editArea.getChildren())
			c.setEnabled(false);
	}

	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (toolThread != null)
			toolThread.interrupt();
	}

	private void addTreeView()
	{
		sashForm = new SashForm(list.getParent(), SWT.HORIZONTAL);
		sashForm.moveAbove(list.getParent());
		final FormData sashData = new FormData();
		sashData.top = new FormAttachment(0);
		sashData.bottom = ((FormData) list.getLayoutData()).bottom;
		sashData.left = new FormAttachment(0);
		sashData.right = new FormAttachment(100);
		sashForm.setLayoutData(sashData);
		tree = new Tree(sashForm, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
		tree.setFont(Main.font);
		list.setParent(sashForm);

		detailPane = new Composite(sashForm, SWT.NONE);
		detailPane.setVisible(false);
		detailPane.setLayout(new FillLayout());

		tree.addSelectionListener(adapt(this::updateDetailPane));

		sashForm.setWeights(new int[] { 1, 3, 3 });
	}

	private void addTreeViewOption()
	{
		final Button treeview = new Button(editArea, SWT.TOGGLE);
		treeview.setText("Tree View");
		treeview.setSelection(true);
		treeview.setToolTipText(hideTree);
		treeview.addSelectionListener(adapt(this::toggleTreeView));
	}

	private void addTableEditor(final Table table)
	{
		editor = new TableEditor(table);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;
		table.addListener(SWT.MouseDown, this::onMouseDown);
	}

	private void toggleTreeView(final SelectionEvent e)
	{
		final Button treeview = (Button) e.widget;
		asyncAddLog(treeview.getToolTipText());
		if (treeview.getSelection()) {
			treeview.setToolTipText(hideTree);
			sashForm.setMaximizedControl(null);
			final TreeItem[] selection = tree.getSelection();
			if (selection.length > 0) {
				final TreeItem item = selection[0];
				if (item.getParentItem() == null)
					showInterfaceObject(tree.indexOf(item));
				else
					showPropertyPage(tree.indexOf(item.getParentItem()), (Integer) item.getData(Columns.Pid.name()));
			}
		}
		else {
			treeview.setToolTipText(showTree);
			showFullTable();
		}
	}

	private void updateDetailPane(final SelectionEvent e)
	{
		final TreeItem item = (TreeItem) e.item;
		if (item == null)
			return;
		if (item.getParentItem() == null)
			showInterfaceObject(tree.indexOf(item));
		else
			showPropertyPage(tree.indexOf(item.getParentItem()), (Integer) item.getData(Columns.Pid.name()));
	}

	private void showInterfaceObject(final int objectIndex)
	{
		hidePropertyPage();
		resetTable();
		for (final Description d : descriptions) {
			if (d.getObjectIndex() == objectIndex)
				addProperty(d);
		}
		sashForm.layout();
	}

	private void showPropertyPage(final int objectIndex, final int pid)
	{
		hidePropertyPage();
		list.setVisible(false);
		detailPane.setVisible(true);
		propertyPage = new Composite(detailPane, SWT.NONE);
		GridLayout layout = new GridLayout(3, false);
		layout.marginHeight = 0;
		layout.verticalSpacing = 1;
		propertyPage.setLayout(layout);
		final Description d = findDescription(objectIndex, pid);
		final PropertyClient.Property p = getDefinition(d.getObjectType(), d.getPID()).orElse(unknown);

		final Composite caption = new Composite(propertyPage, SWT.FILL);
		layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginLeft = 0;
		layout.horizontalSpacing = 7;
		caption.setLayout(layout);
		GridData gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
		gridData.horizontalSpan = 3;
		caption.setLayoutData(gridData);

		final Label description = new Label(caption, SWT.NONE);
		description.setText(p.getName());
		final FontData fontData = description.getFont().getFontData()[0];
		description.setFont(new Font(Main.display, new FontData(fontData.getName(), fontData.getHeight(), SWT.BOLD)));
		gridData = new GridData();
		gridData.verticalAlignment = SWT.BOTTOM;
		description.setLayoutData(gridData);

		final Label ro = new Label(caption, SWT.NONE);
		ro.setText(d.isWriteEnabled() ? "" : "(read-only)");
		gridData = new GridData();
		gridData.verticalAlignment = SWT.BOTTOM;
		ro.setLayoutData(gridData);

		singleLineLabel(p.getPIDName() + " (" + pid + ")");
		singleLineLabel("Located at index " + d.getPropIndex());

		final String rw = d.getReadLevel() + "/" + d.getWriteLevel();
		label("Required access level " + rw, false);

		final Text authCode = new Text(propertyPage, SWT.BORDER);
		authCode.setMessage("Authorization code");
		gridData = new GridData();
		gridData.grabExcessHorizontalSpace = true;
		gridData.horizontalAlignment = SWT.FILL;
		authCode.setLayoutData(gridData);
		authCode.setTextLimit(2 + 4 * 2); // 0x prefix with max 4 bytes of hex
		authCode.addListener(SWT.Verify, e -> {
			final char[] chars = e.text.toLowerCase().toCharArray();
			for (final char c : chars)
				if (!('0' <= c && c <= '9') && !('a' <= c && c <= 'f') && c != 'x')
					e.doit = false;
		});
		final Button authorize = new Button(propertyPage, SWT.PUSH);
		authorize.setText("Authorize");
		authorize.addSelectionListener(adapt(e -> authorize(authCode.getText())));

		label("Property values", false);
		final Text value = new Text(propertyPage, SWT.BORDER);
		gridData = new GridData();
		gridData.horizontalAlignment = SWT.FILL;
		value.setLayoutData(gridData);
		value.setMessage("Use space or comma for multiple values");
		value.setText(formatted(d.getObjectType(), pid, values.getOrDefault(d, "")));
		value.setData("property-edit-field");

		final Button set = new Button(propertyPage, SWT.PUSH);
		set.setText("Set");

		final Label altFormats = new Label(propertyPage, SWT.RIGHT);
		altFormats.setText("Decimal\nHex\nBinary");
		gridData = new GridData();
		gridData.horizontalAlignment = SWT.RIGHT;
		altFormats.setLayoutData(gridData);

		final Text altFormatted = new Text(propertyPage, SWT.READ_ONLY | SWT.WRAP);
		altFormatted.setText(altFormatted(values.getOrDefault(d, ""), ""));
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalAlignment = SWT.FILL;
		altFormatted.setLayoutData(gridData);
		altFormatted.setData("property-alt-formatted");
		label("", false);

		value.addModifyListener(e -> altFormatted.setText(altFormatted(value.getText(), altFormatted.getText())));

		label("Current elements", false);
		final Spinner elems = new Spinner(propertyPage, SWT.BORDER | SWT.RIGHT);
		elems.setData("current-elements");
		elems.setEnabled(false);
		elems.setValues(d.getCurrentElements(), 0, Math.max(d.getCurrentElements(), d.getMaxElements()), 0, 1, 10);
		gridData = new GridData();
		gridData.horizontalAlignment = SWT.FILL;
		elems.setLayoutData(gridData);

		final Button override = new Button(propertyPage, SWT.CHECK);
		override.setText("Override");
		override.addSelectionListener(adapt(e -> elems.setEnabled(override.getSelection())));

		set.addSelectionListener(adapt(e -> writeValues(objectIndex, pid, value.getText(),
				override.getSelection() ? Integer.parseInt(elems.getText()) : -1, p)));

		singleLineLabel("(Maximum elements " + (d.getMaxElements() > 0 ? d.getMaxElements() : "unknown") + ")");

		if (!d.isWriteEnabled()) {
			authCode.setEnabled(false);
			authorize.setEnabled(false);
			set.setEnabled(false);
			value.setEditable(false);
			override.setEnabled(false);
		}

		updateDptBounds("" + d.getObjectType(), "" + pid);

		sashForm.layout(false, true);
	}

	private void hidePropertyPage()
	{
		detailPane.setVisible(false);
		if (propertyPage != null)
			propertyPage.dispose();
		propertyPage = null;
	}

	private Optional<Control> findPropertyPageControl(final int idx, final int pid, final String controlId)
	{
		if (tree.getSelection().length > 0) {
			final TreeItem item = tree.getSelection()[0];
			if (item.getParentItem() != null && (Integer) item.getData(ObjectIndex) == idx
					&& (Integer) item.getData(Columns.Pid.name()) == pid)
				for (final Control c : propertyPage.getChildren())
					if (controlId.equals(c.getData()))
						return Optional.of(c);
		}
		return Optional.empty();
	}

	private void showFullTable()
	{
		hidePropertyPage();
		resetTable();
		sashForm.setMaximizedControl(list);
		for (int i = 0; i < descriptions.size(); i++) {
			addRow(i);
		}
	}

	private static void setFieldSize(final Combo field, final int columns)
	{
		final GC gc = new GC(field);
		final FontMetrics fm = gc.getFontMetrics();
		final int width = columns * fm.getAverageCharWidth();
		gc.dispose();
		field.setLayoutData(new RowData(field.computeSize(width, 0).x, SWT.DEFAULT));
	}

	private boolean isNewInterfaceObject(final int index)
	{
		return index <= 0 || descriptions.get(index).getObjectIndex() != descriptions.get(index - 1).getObjectIndex();
	}

	private void addInterfaceObjectToTree(final int interfaceObject, final int objectType)
	{
		Main.syncExec(() -> {
			final TreeItem item = new TreeItem(tree, SWT.NONE);
			item.setText(PropertyClient.getObjectTypeName(objectType) + " (Object Type " + objectType + ")");
			item.setData(ObjectIndex, interfaceObject);
		});
	}

	private void addPropertyToTree(final int objectType, final int pid)
	{
		Main.syncExec(() -> {
			final TreeItem root = tree.getItem(tree.getItemCount() - 1);
			final TreeItem item = new TreeItem(root, SWT.NONE);
			final String name = "PID " + pid;
			item.setText(getDefinition(objectType, pid).map(p -> p.getPIDName() + " (" + name + ")").orElse(name));
			item.setData(ObjectIndex, root.getData(ObjectIndex));
			item.setData(Columns.Pid.name(), pid);
		});
	}

	private void addRow(final int index)
	{
		final Description d = descriptions.get(index);
		if (isNewInterfaceObject(index)) {
			final String[] keys = new String[] { ObjectHeader, ObjectIndex, ObjectType, };
			final String[] data = new String[] { "", "" + d.getObjectIndex(), "" + d.getObjectType() };
			count = 0;
			asyncAddListItem(new String[Columns.values().length], keys, data);
		}
		addProperty(d);
	}

	private void addProperty(final Description d)
	{
		final String[] keys = new String[] { ObjectIndex, ObjectType, };
		final String[] data = new String[] { "" + d.getObjectIndex(), "" + d.getObjectType() };

		final PropertyClient.Property p = getDefinition(d.getObjectType(), d.getPID()).orElse(unknown);
		final String name = p.getPIDName();
		final String desc = p.getName();
		final String rw = d.getReadLevel() + "/" + d.getWriteLevel() + (d.isWriteEnabled() ? "" : " (read-only)");

		final String value = formatted(d.getObjectType(), d.getPID(), values.getOrDefault(d, ""));
		final String[] item = { "" + count++, "" + d.getPID(), name, "" + d.getCurrentElements(), value, rw, desc };
		asyncAddListItem(item, keys, data);
	}

	private void resetTable()
	{
		list.setVisible(true);
		list.removeAll();
		count = 0;
	}

	private void writeValues(final int objectIndex, final int pid, final String values, final int elems,
		final PropertyClient.Property p)
	{
		int typeSize = -1;
		final DPTID dptid = PropertyTypes.getAllPropertyTypes().get(p.getPDT());
		if (dptid != null)
			typeSize = dptSize(pid, dptid.getMainNumber(), dptid.getDPT());
		if (typeSize == -1 && p.getDPT() != null)
			typeSize = dptSize(pid, 0, p.getDPT());
		if (typeSize == -1)
			typeSize = 1;

		final String data;
		final int elements;
		// special case for KNXnet/IP friendly name: 30 chars
		if (p.getObjectType() == 11 && pid == 76) {
			byte[] bytes = new byte[0];
			try {
				bytes = values.getBytes("ISO-8859-1");
			}
			catch (final UnsupportedEncodingException na) {}
			final byte[] array = Arrays.copyOf(bytes, 30);
			data = "0x" + DataUnitBuilder.toHex(array, "");
			elements = array.length;
		}
		else {
			final String format = "%0" + typeSize * 2 + "x";
			final AtomicInteger count = new AtomicInteger(0);
			data = Stream.of(values.split("\\s+|,")).filter(s -> !s.isEmpty()).peek(s -> count.incrementAndGet())
					.map(Long::decode).map(l -> String.format(format, l)).collect(Collectors.joining("", "0x", ""));
			elements = elems > 0 ? elems : count.get();
		}
		runCommand("set", objectIndex, pid, "1", elements, data);
		// update our description to reflect the valid number of elements
		runCommand("desc", objectIndex, pid);
		// read maximum number of values back (number of elements might have changed)
		// TODO we should wait until description got updated by "desc" command
		final int readBack = Math.max(elements, findDescription(objectIndex, pid).getCurrentElements());
		runCommand("get", objectIndex, pid, "1", readBack);
	}

	private int dptSize(final int pid, final int main, final String dpt)
	{
		int size = -1;
		try {
			size = Math.max(1, TranslatorTypes.createTranslator(main, dpt).getTypeSize());
			asyncAddLog("PID " + pid + " has type size " + size + " (DPT " + dpt + ")");
		}
		catch (final KNXException ignore) {}
		return size;
	}

	private static String altFormatted(final String values, final String onError)
	{
		try {
			final List<Long> tmp = Stream.of(values.split("\\s+|,")).filter(s -> !s.isEmpty()).map(Long::decode)
					.collect(Collectors.toList());
			final String dec = tmp.stream().map(Object::toString).collect(Collectors.joining(", "));
			final String hex = tmp.stream().map(Long::toHexString).collect(Collectors.joining(", "));;
			final String bin = tmp.stream().map(Long::toBinaryString).collect(Collectors.joining(", "));
			return dec + "\n" + hex + "\n" + bin;
		}
		catch (final RuntimeException e) {}
		return onError;
	}

	private Label label(final String text, final boolean singleLine)
	{
		final Label l = new Label(propertyPage, SWT.NONE);
		l.setText(text);
		final GridData gridData = new GridData();
		gridData.horizontalSpan = singleLine ? 3 : 1;
		l.setLayoutData(gridData);
		return l;
	}

	private Label singleLineLabel(final String text)
	{
		return label(text, true);
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
				@SuppressWarnings("fallthrough")
				final Listener textListener = new Listener() {
					@Override
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
							default: // nop
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

				updateDptBounds((String) item.getData(ObjectType), item.getText(Columns.Pid.ordinal()));
				return;
			}
			if (!visible && rect.intersects(clientArea))
				visible = true;
			if (!visible)
				return;
			index++;
		}
	}

	private void updateDptBounds(final SelectionEvent e)
	{
		final TableItem item = (TableItem) e.item;
		final String pid = item.getText(Columns.Pid.ordinal());
		// skip table section header
		if (pid.isEmpty())
			bounds.removeAll();
		else
			updateDptBounds((String) item.getData(ObjectType), pid);
	}

	private void updateDptBounds(final String objType, final String pid)
	{
		bounds.removeAll();

		final Optional<PropertyClient.Property> opt = getDefinition(Integer.parseInt(objType), Integer.parseInt(pid));
		if (!opt.isPresent()) {
			bounds.add("n/a");
			bounds.select(0);
			return;
		}
		final PropertyClient.Property p = opt.get();

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
		if (t == null) {
			bounds.add("n/a");
			bounds.select(0);
			return;
		}

		final DPT dpt = t.getType();
		bounds.add(dpt.getLowerValue());
		bounds.add(dpt.getUpperValue());
		bounds.select(1);
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

	private static Optional<PropertyClient.Property> getDefinition(final int objType, final int pid)
	{
		PropertyClient.Property p = map.get(new PropertyKey(objType, pid));
		if (p == null)
			p = map.get(new PropertyKey(PropertyKey.GLOBAL_OBJTYPE, pid));
		return Optional.ofNullable(p);
	}

	private Description findDescription(final int objectIndex, final int pid)
	{
		for (final Description d : descriptions) {
			if (d.getObjectIndex() == objectIndex && d.getPID() == pid)
				return d;
		}
		return null;
	}

	private boolean replaceDescription(final Description update)
	{
		for (int i = 0; i < descriptions.size(); i++) {
			final Description d = descriptions.get(i);
			if (d.getObjectIndex() == update.getObjectIndex() && d.getPID() == update.getPID()) {
				descriptions.set(i, update);
				return true;
			}
		}
		return false;
	}

	private void authorize(final String authCode)
	{
		if (connect.knxAddress.isEmpty()) {
			asyncAddLog("connection uses local property services, no authorization available");
			return;
		}

		toolThread.interrupt();
		try {
			toolThread.join();
		}
		catch (final InterruptedException ignore) {}
		runProperties(Arrays.asList("--authorize", authCode), false);
	}

	private void restart()
	{
		if (connect.knxAddress.isEmpty()) {
			if (askUser("Restart KNX Device " + connect.remote, "Perform local device management reset?") != SWT.YES)
				return;

			toolThread.interrupt();
			try {
				toolThread.join();
			}
			catch (final InterruptedException ignore) {}
			runProperties(Arrays.asList("reset"), false);
			// TODO after M_Reset.req, we need to wait for reset to complete and restart our property tool
			return;
		}
		try {
			final IndividualAddress device = new IndividualAddress(connect.knxAddress);
			if (askUser("Restart KNX Device " + device,
					"Perform a confirmed restart in connection-less mode?") != SWT.YES)
				return;
			try (ManagementClientImpl mgmt = new ManagementClientImpl(toolLink)) {
				final Destination dst = mgmt.createDestination(device, false);
				final int eraseCode = 1; // confirmed restart
				try {
					final int time = mgmt.restart(dst, eraseCode, 0);
					asyncAddLog("Restarting device will take " + (time == 0 ? " ≤ 5 " : time) + " seconds");
				}
				catch (final KNXTimeoutException e) {
					asyncAddLog("Resort to basic restart (" + e.getMessage() + ")");
					mgmt.restart(dst);
				}
			}
		}
		catch (KNXException | InterruptedException e) {
			asyncAddLog(e);
		}
	}

	private int askUser(final String title, final String msg)
	{
		final int style = SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL | SWT.SHEET;
		final MessageBox dlg = new MessageBox(Main.shell, style);
		dlg.setText(title);
		dlg.setMessage(msg);
		final int id = dlg.open();
		final String response;
		switch (id) {
		case SWT.YES:
			response = "yes";
			break;
		case SWT.NO:
			response = "no";
			break;
		case SWT.CANCEL:
			response = "canceled";
			break;
		default:
			response = "button " + id;
		}
		asyncAddLog(title + ": " + response);
		return id;
	}

	private void runCommand(final String... cmd)
	{
		synchronized (commands) {
			commands.add(cmd);
			commands.notify();
		}
	}

	private void runCommand(final Object... cmd)
	{
		runCommand(Stream.of(cmd).map(Object::toString).toArray(String[]::new));
	}

	private void runProperties(final List<String> cmd, final boolean init)
	{
		// setup tool argument array
		final List<String> args = new ArrayList<String>();
		args.add("--verbose");
		args.addAll(connect.getArgs(true));
		args.addAll(cmd);
		asyncAddLog("Using command line: " + String.join(" ", args));
		setHeaderInfo(statusInfo(0));

		toolThread = new Thread("Calimero property tool") {
			@Override
			public void run()
			{
				try {
					final Property tool = new Property(args.toArray(new String[args.size()])) {
						@Override
						protected void runCommand(final String... cmd) throws InterruptedException
						{
							toolLink = link();
							if (init) {
								Main.asyncExec(() -> setHeaderInfo(statusInfo(1)));
								pc.addDefinitions(definitions);
								map.putAll(pc.getDefinitions());

								super.runCommand(cmd);

								for (final Description d : descriptions) {
									try {
										super.runCommand("get", "" + d.getObjectIndex(), "" + d.getPID(), "1",
												"" + d.getCurrentElements());
									}
									catch (final RuntimeException e) {
										asyncAddLog(e.toString());
									}
								}
								Main.asyncExec(() -> {
									if (editArea.isDisposed())
										return;
									for (final Control c : editArea.getChildren())
										c.setEnabled(true);
									setHeaderInfo(statusInfo(2));
								});
							}
							while (true) {
								final String[] command;
								synchronized (commands) {
									while (commands.isEmpty())
										commands.wait();
									command = commands.remove(0);
								}
								asyncAddLog(String.join(" ", command));
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
							if (replaceDescription(d)) {
								Main.asyncExec(() -> {
									find(d.getObjectIndex(), d.getPID()).ifPresent(
											i -> i.setText(Columns.Elements.ordinal(), "" + d.getCurrentElements()));
									findPropertyPageControl(d.getObjectIndex(), d.getPID(), "current-elements")
											.ifPresent(c -> ((Spinner) c).setValues(d.getCurrentElements(), 0,
													Math.max(d.getCurrentElements(), d.getMaxElements()), 0, 1, 10));
								});
								return;
							}
							descriptions.add(d);
							addRow(descriptions.size() - 1);
							if (isNewInterfaceObject(descriptions.size() - 1))
								addInterfaceObjectToTree(d.getObjectIndex(), d.getObjectType());
							addPropertyToTree(d.getObjectType(), d.getPID());
						}

						@Override
						protected void onPropertyValue(final int idx, final int pid, final String value)
						{
							final Description d = findDescription(idx, pid);
							values.put(d, value);
							Main.asyncExec(() -> {
								if (editArea.isDisposed())
									return;
								final String text = formatted(d.getObjectType(), pid, value);
								find(idx, pid).ifPresent(i -> i.setText(Columns.Values.ordinal(), text));
								findPropertyPageControl(idx, pid, "property-edit-field")
										.ifPresent(c -> ((Text) c).setText(text));
								findPropertyPageControl(idx, pid, "property-alt-formatted")
										.ifPresent(c -> ((Text) c).setText(altFormatted(value, ((Text) c).getText())));
							});
						}

						@Override
						protected void onCompletion(final Exception thrown, final boolean canceled)
						{
							if (thrown != null) {
								asyncAddLog(thrown.toString());
							}
							Main.asyncExec(() -> setHeaderInfo(statusInfo(2)));
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

	// phase: 0=connecting, 1=reading, 2=completed, x=unknown
	private String statusInfo(final int phase)
	{
		final String status = phase == 0 ? "Connecting to"
				: phase == 1 ? "Reading properties of" : phase == 2 ? "Completed reading properties of" : "Unknown";
		final String device = connect.knxAddress.isEmpty() ? "" : " device " + connect.knxAddress;
		return status + device + (connect.remote == null ? "" : " " + connect.remote) + " on port " + connect.port
				+ (connect.useNat() ? " (using NAT)" : "");
	}

	// assumes we're running on the main thread
	private Optional<TableItem> find(final int oi, final int pid)
	{
		for (final TableItem item : list.getItems()) {
			if (item.getData(ObjectIndex).equals("" + oi) && item.getText(Columns.Pid.ordinal()).equals("" + pid))
				return Optional.of(item);
		}
		return Optional.empty();
	}

	private SelectionListener adapt(final Consumer<SelectionEvent> c)
	{
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				try {
					c.accept(e);
				}
				catch (final RuntimeException rte) {
					asyncAddLog(rte);
				}
			}
		};
	}

	private String formatted(final int objectType, final int pid, final String value)
	{
		if (value.isEmpty())
			return value;
		try {
			if (pid == PID.TABLE)
				return asGroupAddresses(value);
			if (pid == PID.DESCRIPTION)
				return asString(value);

			if (objectType == 11) {
				if (pid == PID.ADDITIONAL_INDIVIDUAL_ADDRESSES || pid == PID.KNX_INDIVIDUAL_ADDRESS)
					return asIndividualAddresses(value);
				if (pid == PID.FRIENDLY_NAME)
					return asString(value);
				if (pid == PID.IP_ADDRESS || pid == PID.SUBNET_MASK || pid == PID.CURRENT_IP_ADDRESS
						|| pid == PID.CURRENT_DEFAULT_GATEWAY || pid == PID.CURRENT_SUBNET_MASK
						|| pid == PID.DEFAULT_GATEWAY || pid == PID.ROUTING_MULTICAST_ADDRESS
						|| pid == PID.SYSTEM_SETUP_MULTICAST_ADDRESS)
					return asIPAddress(value);
			}
		}
		catch (final RuntimeException e) {
			asyncAddLog("Formatting PID " + pid + " value \"" + value + "\": " + e.toString());
		}
		return value;
	}

	private static String asString(final String value)
	{
		final StringBuilder sb = new StringBuilder();
		for (final String s : strip(value)) {
			final char c = (char) Integer.parseInt(s);
			if (c == 0)
				break;
			sb.append(c);
		}
		return sb.toString();
	}

	private static String asIndividualAddresses(final String value)
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

	private static String asGroupAddresses(final String value)
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

	private static String asIPAddress(final String value)
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

	private static List<String> strip(final String value)
	{
		return Arrays.asList(value.replaceAll("\\[|\\s|\\]", "").split(","));
	}
}
