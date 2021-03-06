/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.logisim.std.wiring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstancePoker;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.util.GraphicsUtil;

public class ProgrammableGenerator extends InstanceFactory {
	public static class ClockLogger extends InstanceLogger {
		@Override
		public String getLogName(InstanceState state, Object option) {
			return state.getAttributeValue(StdAttr.LABEL);
		}

		@Override
		public Value getLogValue(InstanceState state, Object option) {
			// send current value to log window
			ProgrammableGeneratorState s = getState(state);
			return s.sending;
		}
	}

	private static class ContentsAttribute extends Attribute<String> {
		private InstanceState state = null;
		private Instance instance = null;
		private CircuitState circ = null;
		private Component comp = null;

		private ContentsAttribute() {
			super("Contents", Strings.getter("romContentsAttr"));
		}

		@Override
		public java.awt.Component getCellEditor(Window source, String value) {
			Project proj = null;
			if (source instanceof Frame)
				proj = ((Frame) source).getProject();
			ProgrammableGeneratorState data = null;
			if (this.state != null)
				data = getState(state);
			else if (this.instance != null && this.circ != null)
				data = getState(instance, circ);
			else if (this.comp != null && this.circ != null)
				data = getState(comp, circ);
			ContentsCell ret = new ContentsCell(data);
			// call mouse click function and open edit window
			ret.mouseClicked(null);
			// if something changed change the attribute value using the first different
			// from null
			if (this.state != null && !data.getSavedData().equals(state.getAttributeValue(CONTENTS_ATTR))) {
				state.fireInvalidated();
				state.getAttributeSet().setValue(CONTENTS_ATTR, data.getSavedData());
				if (proj != null)
					proj.getLogisimFile().setDirty(true);
			} else if (this.instance != null
					&& !data.getSavedData().equals(instance.getAttributeValue(CONTENTS_ATTR))) {
				instance.fireInvalidated();
				instance.getAttributeSet().setValue(CONTENTS_ATTR, data.getSavedData());
				if (proj != null)
					proj.getLogisimFile().setDirty(true);
			} else if (this.comp != null && this.circ != null
					&& !data.getSavedData().equals(comp.getAttributeSet().getValue(CONTENTS_ATTR))) {
				circ.getInstanceState(comp).fireInvalidated();
				comp.getAttributeSet().setValue(CONTENTS_ATTR, data.getSavedData());
				if (proj != null)
					proj.getLogisimFile().setDirty(true);
			}
			return ret;
		}

		@Override
		public String parse(String value) {
			return value;
		}

		void setData(Component comp, CircuitState circ) {
			if (!comp.equals(this.comp))
				this.comp = comp;
			if (!circ.equals(this.circ))
				this.circ = circ;
		}

		// i don't know other ways to do this, I'll try to change this when I'll know
		// better Burch's code. Save these variables so I can change the contents
		// attribute and get the component's data
		void setData(Instance instance, CircuitState circ) {
			if (!instance.equals(this.instance))
				this.instance = instance;
			if (!circ.equals(this.circ))
				this.circ = circ;
		}

		void setData(InstanceState state) {
			if (!state.equals(this.state))
				this.state = state;
		}

		@Override
		public String toDisplayString(String value) {
			return Strings.get("romContentsValue");
		}

	}

	public static class ContentsCell extends JLabel implements MouseListener {
		/**
		 * 
		 */
		private static final long serialVersionUID = -53754819096800664L;
		private ProgrammableGeneratorState data;

		ContentsCell(ProgrammableGeneratorState data) {
			super(Strings.get("romContentsValue"));
			this.data = data;
			addMouseListener(this);
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			// open edit window when attribute clicked
			if (data == null)
				return;
			data.editWindow();
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}

		@Override
		public void mousePressed(MouseEvent e) {
		}

		@Override
		public void mouseReleased(MouseEvent e) {
		}
	}

	public static class Poker extends InstancePoker {
		@Override
		public void mouseReleased(InstanceState state, MouseEvent e) {
			// click on component with poke tool
			// get component data
			ProgrammableGeneratorState data = getState(state);
			// increment ticks so you can use it step by step
			data.incrementTicks();
			int durationHigh = data.getdurationHighValue();
			int statetick = data.getStateTick();
			// set the next value
			Value desired = (statetick - 1 < durationHigh ? Value.TRUE : Value.FALSE);
			if (!data.sending.equals(desired)) {
				data.sending = desired;
				// set state as dirty
				state.fireInvalidated();
			}
		}
	}

	private static class ProgrammableGeneratorMenu implements ActionListener, MenuExtender {
		private JMenuItem edit;
		private JMenuItem reset;
		private Instance instance;
		private CircuitState circState;

		public ProgrammableGeneratorMenu(Instance instance) {
			this.instance = instance;
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			// when you click a jmenuitem after a right click on component
			ProgrammableGeneratorState data = ProgrammableGenerator.getState(instance, circState);
			if (evt.getSource() == edit)
				data.editWindow();
			else if (evt.getSource() == reset)
				data.clearValues();
			// set .circ to save
			if (!data.getSavedData().equals(instance.getAttributeValue(CONTENTS_ATTR))) {
				instance.fireInvalidated();
				instance.getAttributeSet().setValue(CONTENTS_ATTR, data.getSavedData());
				circState.getProject().getLogisimFile().setDirty(true);
			}
		}

		@Override
		public void configureMenu(JPopupMenu menu, Project proj) {
			this.circState = proj.getCircuitState();
			boolean enabled = circState != null;

			this.edit = createItem(enabled, Strings.get("ramEditMenuItem"));
			this.reset = createItem(enabled, Strings.get("ramClearMenuItem"));
			menu.addSeparator();
			menu.add(this.edit);
			menu.add(this.reset);
		}

		private JMenuItem createItem(boolean enabled, String label) {
			JMenuItem ret = new JMenuItem(label);
			ret.setEnabled(enabled);
			ret.addActionListener(this);
			return ret;
		}
	}

	private static final Attribute<Integer> ATTR_NSTATE = Attributes.forIntegerRange("nState",
			Strings.getter("NStateAttr"), 1, 32);
	private static final ContentsAttribute CONTENTS_ATTR = new ContentsAttribute();

	public static final ProgrammableGenerator FACTORY = new ProgrammableGenerator();

	private static ProgrammableGeneratorState getState(Component comp, CircuitState circ) {
		ProgrammableGeneratorState ret = (ProgrammableGeneratorState) circ.getData(comp);
		int nstate = comp.getAttributeSet().getValue(ATTR_NSTATE);
		if (ret == null) {
			ret = new ProgrammableGeneratorState(nstate);
			// if new, fill the content with the saved data
			ret.decodeSavedData(comp.getAttributeSet().getValue(CONTENTS_ATTR));
			circ.setData(comp, ret);
		} else if (ret.updateSize(nstate)) {
			// if size updated, update the content attribute, written here because can't
			// access PlaRomData object from instanceAttributeChanged method
			comp.getAttributeSet().setValue(CONTENTS_ATTR, ret.getSavedData());
		}
		CONTENTS_ATTR.setData(comp, circ);
		return ret;
	}

	private static ProgrammableGeneratorState getState(Instance state, CircuitState circ) {
		ProgrammableGeneratorState ret = (ProgrammableGeneratorState) state.getData(circ);
		int nstate = state.getAttributeValue(ATTR_NSTATE);
		if (ret == null) {
			ret = new ProgrammableGeneratorState(nstate);
			// if new, fill the content with the saved data
			ret.decodeSavedData(state.getAttributeValue(CONTENTS_ATTR));
			state.setData(circ, ret);
		} else if (ret.updateSize(nstate)) {
			// if size updated, update the content attribute, written here because can't
			// access PlaRomData object from instanceAttributeChanged method
			state.getAttributeSet().setValue(CONTENTS_ATTR, ret.getSavedData());
		}
		CONTENTS_ATTR.setData(state, circ);
		return ret;
	}

	private static ProgrammableGeneratorState getState(InstanceState state) {
		ProgrammableGeneratorState ret = (ProgrammableGeneratorState) state.getData();
		int nstate = state.getAttributeValue(ATTR_NSTATE);
		if (ret == null) {
			ret = new ProgrammableGeneratorState(nstate);
			// if new, fill the content with the saved data
			ret.decodeSavedData(state.getAttributeValue(CONTENTS_ATTR));
			state.setData(ret);
		} else if (ret.updateSize(nstate)) {
			// if size updated, update the content attribute, written here because can't
			// access PlaRomData object from instanceAttributeChanged method
			state.getAttributeSet().setValue(CONTENTS_ATTR, ret.getSavedData());
		}
		CONTENTS_ATTR.setData(state);
		return ret;
	}

	//
	// package methods
	//
	public static boolean tick(CircuitState circState, int ticks, Component comp) {
		ProgrammableGeneratorState state = getState(comp, circState);
		state.incrementTicks();
		int statetick = state.getStateTick();
		Value desired = Value.UNKNOWN;
		if (statetick > 0) {
			int durationHigh = state.getdurationHighValue();
			desired = (statetick - 1 < durationHigh ? Value.TRUE : Value.FALSE);
		}
		if (!state.sending.equals(desired)) {
			state.sending = desired;
			Instance.getInstanceFor(comp).fireInvalidated();
			return true;
		} else {
			return false;
		}
	}

	public ProgrammableGenerator() {
		super("ProgrammableGenerator", Strings.getter("ProgrammableGeneratorComponent"));
		setAttributes(
				new Attribute[] { StdAttr.FACING, ATTR_NSTATE, StdAttr.LABEL, Pin.ATTR_LABEL_LOC, StdAttr.LABEL_FONT,
						StdAttr.ATTR_LABEL_COLOR, CONTENTS_ATTR },
				new Object[] { Direction.EAST, Integer.valueOf(4), "", Direction.WEST, StdAttr.DEFAULT_LABEL_FONT,
						Color.BLACK, "" });
		setFacingAttribute(StdAttr.FACING);
		setInstanceLogger(ClockLogger.class);
		setInstancePoker(Poker.class);
		setIconName("programmablegenerator.gif");
	}

	//
	// private methods
	//
	private void configureLabel(Instance instance) {
		Direction facing = instance.getAttributeValue(StdAttr.FACING);
		Direction labelLoc = instance.getAttributeValue(Pin.ATTR_LABEL_LOC);
		Probe.configureLabel(instance, labelLoc, facing);
	}

	//
	// methods for instances
	//
	@Override
	protected void configureNewInstance(Instance instance) {
		instance.addAttributeListener();
		instance.setPorts(new Port[] { new Port(0, 0, Port.OUTPUT, BitWidth.ONE) });
		configureLabel(instance);
	}

	@Override
	protected Object getInstanceFeature(Instance instance, Object key) {
		if (key == MenuExtender.class) {
			return new ProgrammableGeneratorMenu(instance);
		}
		return super.getInstanceFeature(instance, key);
	}

	@Override
	public Bounds getOffsetBounds(AttributeSet attrs) {
		return Probe.getOffsetBounds(attrs.getValue(StdAttr.FACING), BitWidth.ONE, RadixOption.RADIX_2);
	}

	@Override
	protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
		if (attr == Pin.ATTR_LABEL_LOC) {
			configureLabel(instance);
		} else if (attr == StdAttr.FACING) {
			instance.recomputeBounds();
			configureLabel(instance);
		}
	}

	@Override
	public void paintInstance(InstancePainter painter) {
		Graphics2D g = (Graphics2D) painter.getGraphics();
		Bounds bds = painter.getInstance().getBounds();
		int x = bds.getX();
		int y = bds.getY();
		g.setColor(painter.getAttributeValue(StdAttr.ATTR_LABEL_COLOR));
		painter.drawLabel();
		g.setColor(Color.BLACK);
		boolean drawUp;
		if (painter.getShowState()) {
			ProgrammableGeneratorState state = getState(painter);
			painter.drawRoundBounds(state.sending.getColor());
			drawUp = state.sending == Value.TRUE;
		} else {
			painter.drawBounds(Color.BLACK);
			drawUp = true;
		}
		g.setColor(Color.WHITE);
		x += 10;
		y += 10;
		int[] xs = { x + 1, x + 1, x + 4, x + 4, x + 7, x + 7 };
		int[] ys;
		if (drawUp) {
			ys = new int[] { y + 5, y + 3, y + 3, y + 7, y + 7, y + 5 };
		} else {
			ys = new int[] { y + 5, y + 7, y + 7, y + 3, y + 3, y + 5 };
		}
		g.drawPolyline(xs, ys, xs.length);
		GraphicsUtil.switchToWidth(g, 2);
		xs = new int[] { x - 5, x - 5, x + 1, x + 1, x - 4 };
		ys = new int[] { y + 5, y - 5, y - 5, y, y };
		g.drawPolyline(xs, ys, xs.length);
		painter.drawPorts();
	}

	@Override
	public void propagate(InstanceState state) {
		Value val = state.getPort(0);
		ProgrammableGeneratorState q = getState(state);
		if (!val.equals(q.sending)) { // ignore if no change
			state.setPort(0, q.sending, 1);
		}
	}
}
