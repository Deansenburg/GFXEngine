package GxEngine3D.Model;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Polygon;
import GxEngine3D.Controller.GXController;
import GxEngine3D.View.ViewHandler;
import Shapes.BaseShape;

public class Polygon2D {
	Polygon P;
	Color c;
	boolean draw = true;
	double lighting = 1;

	private ViewHandler vHandler;

	BaseShape belongsTo;
	Polygon3D belongsToPoly;

	private boolean hover = false;

	public Polygon2D(double[] x, double[] y, Color c,
					 ViewHandler v, BaseShape bToShape, Polygon3D bToPoly) {
		belongsTo = bToShape;
		belongsToPoly = bToPoly;
		vHandler = v;
		P = new Polygon();
		for (int i = 0; i < x.length; i++) {
			P.addPoint((int) x[i], (int) y[i]);
		}
		this.c = c;
	}

	public void hover()
	{
		hover = true;
	}

	public Polygon getPolygon()
	{
		return P;
	}

	public void updatePolygon(double[] x, double[] y) {
		P = new Polygon();
		for (int i=0;i<x.length;i++)
		{
			P.addPoint((int)x[i], (int)y[i]);
		}
	}

	public Polygon3D getBelongsToPolygon() {
		return belongsToPoly;
	}

	public void drawPolygon(Graphics g) {
		//to edit brightness directly convert to hsb
		float[] temp = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), new float[3]);
		g.setColor(new Color(Color.HSBtoRGB(temp[0], temp[1], (float) lighting)));
		belongsTo.draw(g, P);
		if (hover && vHandler.canHover()) {
			g.setColor(new Color(255, 255, 255, 100));
			belongsTo.drawHighlight(g, P);
		}
		if (vHandler.hasOutlines()) {
			g.setColor(new Color(0, 0, 0));
			belongsTo.drawOutlines(g, P);
		}
		hover = false;
	}

	public boolean isMouseOver() {
		int[] center = vHandler.getCentre();
		return P.contains(center[0], center[1]);
	}

	public boolean canDraw()
	{
		return draw;
	}
}
