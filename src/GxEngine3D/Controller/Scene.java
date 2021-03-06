package GxEngine3D.Controller;

import java.util.*;

import GxEngine3D.Model.Matrix.AlgebraicMatrix;
import GxEngine3D.Helper.VectorCalc;
import GxEngine3D.Camera.Camera;
import DebugTools.TextOutput;
import GxEngine3D.Lighting.Light;
import GxEngine3D.Model.*;
import GxEngine3D.Ordering.IOrderStrategy;
import GxEngine3D.View.PolygonIterator;
import GxEngine3D.View.ViewHandler;
import Shapes.IShape;

public class Scene implements ITickListener {

	private ArrayList<IShape> shapes = new ArrayList<IShape>();

	private ArrayList<Polygon3D> polygons = new ArrayList<Polygon3D>();
	private ArrayList<Polygon3D> splitPolygons = new ArrayList<Polygon3D>();
	private Map<ViewHandler, ArrayList<Polygon2D>> drawablePolygons = new HashMap<>();

	Light lightSource;
	IOrderStrategy orderStrategy;

	private boolean globalRedraw = true, needsUpdate = false;
	public boolean shouldSplit = false;
	
	public Scene(Light ls, IOrderStrategy o) {
		super();
		lightSource = ls;
		orderStrategy = o;
	}

	public void scheduleUpdate()
	{
		if (!needsUpdate)
		{
			needsUpdate = true;
			//if we're changing the polygons we should redraw them
			scheduleRedraw();
		}
	}

	public void scheduleRedraw()
	{
		if (!globalRedraw)
		{
			globalRedraw = true;
		}
	}

	public void setSplitting(boolean b)
	{
		shouldSplit = b;
	}

	public void addObject(IShape s) {
		shapes.add(s);
		scheduleUpdate();
	}
	
	public ArrayList<IShape> getShapes()
	{
		return (ArrayList<IShape>) shapes.clone();
	}

	//NOTE: its extremely rare that the "same" order would be needed twice in a row so caching order should only give minimal improvements
	public PolygonIterator getIterator(ViewHandler vH)
	{
		if (drawablePolygons.containsKey(vH)) {
			//we only need to order the polygons when we're about to draw them instead of every time we update the polygons
			List<Polygon2D> drawable = (List<Polygon2D>) drawablePolygons.get(vH).clone();
			ArrayList<Polygon3D> copy = new ArrayList<>();

			//reducing the amount of things to order to aid performance
			//also we're ot using BSP yet so having order for things we can't see isn't advantageous
			for (Polygon2D dp:drawable)
			{
				copy.add(dp.getBelongsToPolygon());
			}
			List<Integer> o = orderStrategy.order(vH.getCamera().From(), copy);
			setPolyHover(drawable, o);
			return new PolygonIterator(drawable, o);
		}
		//the views want to render before we're setup, bug?
		TextOutput.println(vH.hashCode() + " is null");
		return null;
	}
	
	public void update(ViewHandler v) {
		boolean update = needsUpdate, redraw = v.canRedraw() || globalRedraw;
		needsUpdate = false;
		v.setRedraw(false);
		Camera cam = v.getCamera();
		if (update){
			//somethings changed but we don't know what, either:
			//-a shape was added
			//-(future)a shape was removed
			polygons.clear();
			for (IShape s : shapes) {
				for (Polygon3D p : s.getShape()) {
					polygons.add(p);
				}
			}
		}
		//polygons being redrawn is based on whether they've changed onscreen
		//this can happen either:
		//-moving the camera
		//-moving the polygon
		//so we still need to update polygon in case its trying to move
		if (redraw) {
			for (IShape s : shapes) {
				//TODO check whether the shape needs to be updated first
				s.update();
			}
		}
		if (update) {
			updateSplitting();
		}
		if (redraw) {
			lightSource.updateLighting();
			cam.setup();
			ArrayList<Polygon2D> draw = new ArrayList<>();

			for (Polygon3D poly : splitPolygons) {
				Polygon2D dPoly = poly.updatePolygon(cam, lightSource, v);
				//reduce the number of polygons being ordered by removing those that are not in view
				if (dPoly.canDraw()) {
					draw.add(dPoly);
				}
			}
			drawablePolygons.put(v, draw);
		}
	}

	private void setPolyHover(List<Polygon2D> polys, List<Integer> order) {
		Polygon2D dp;
		for (int i = polys.size() - 1; i >= 0; i--) {
			int pos = order.get(i);
			dp = polys.get(pos);
				if (dp.isMouseOver()) {
					TextOutput.println(pos, 2);
					dp.hover();
					break;
			}
		}
	}

	public void updateSplitting() {
		ArrayList<Polygon3D> splitPolygons = (ArrayList<Polygon3D>) polygons.clone();
		if (shouldSplit) {
			TextOutput.println("Start " + splitPolygons.size(), 1);
			for (int i = 0; i < splitPolygons.size(); i++) {
				//find line intersection between the planes
				Polygon3D p1 = splitPolygons.get(i);
				if (p1.getShape().length <= 2) continue;//this is a line or a point
				Plane plane01 = new Plane(p1);

				ArrayList<Polygon3D> copy = (ArrayList<Polygon3D>) splitPolygons.clone();
				//items behind i have been entirely checked so no need to keep checking them
				for (int ii = i + 1; ii < copy.size(); ii++) {
					TextOutput.println("Split Index " + i + " " + ii, 1);
					Polygon3D p2 = copy.get(ii);
					if (p2.getShape().length <= 2) continue;
					Plane plane02 = new Plane(p2);
					//the same planes can have extremely small differences that the matrix see's them as different planes
					//technically we should also check their relativePoints but if they are parallel thenno split really makes sense
					if (VectorCalc.v_v_equals(plane01.getNV().toArray(), plane02.getNV().toArray())) {
						TextOutput.println("Is same plane", 1);
						continue;
					}
					AlgebraicMatrix m = new AlgebraicMatrix(2, 4);
					m.addEqautionOfPlane(plane01);
					m.addEqautionOfPlane(plane02);
					m.gaussJordandElimination();
					m.determineSolution();
					if (m.getSolutionType() == AlgebraicMatrix.SolutionType.LINE) {
						//find point intersection between the line intersection and the edges of the poly
						SplittingPackage[] line = splitPolygon(p1, m);
						if (line != null) {
							//find the point intersection for the other poly
							SplittingPackage[] line02 = splitPolygon(p2, m);
							if (line02 != null) {
								//we don't always want to add both splits, sometimes it will split on an already existing edge thus generating an identical polygon
								boolean b1 = !alreadyExists(line, p1), b2 = !alreadyExists(line02, p2);
								TextOutput.println("b1: " + b1 + " b2: " + b2, 1);
								if (b1) {
									Polygon3D[] splits01 = p1.splitAlong(line);
									splitPolygons.remove(i);
									for (Polygon3D p : splits01) {
										splitPolygons.add(i, p);
									}
								}
								if (b2) {
									Polygon3D[] splits02 = p2.splitAlong(line02);
									if (!b1) {
										splitPolygons.remove(ii);
									} else {
										splitPolygons.remove(ii + 1);//removes i then adds 2, so -1 + 2 == +1
									}
									for (Polygon3D p : splits02) {
										splitPolygons.add(p);
									}
								}
								if (b1 || b2) {
									TextOutput.println("Split done", 1);
									i--;
									break;//we split the polygon and it no longer exists so this iteration needs to stop
								}
							} else {
								TextOutput.println("Line02 is null", 1);
							}
						} else {
							TextOutput.println("Line01 is null", 1);
						}
					} else {
						TextOutput.println("Not a Line Solution: " + m.getSolutionType(), 1);
					}
				}
			}
			TextOutput.println("End "+splitPolygons.size(), 1);
		}
		this.splitPolygons = splitPolygons;
	}

	private boolean alreadyExists(SplittingPackage[] pack, Polygon3D poly)
	{
		RefPoint3D[] shape = poly.getShape();
		int i, j;
		for (i = 0, j = shape.length-1; i < shape.length; j = i++)
		{
			AlgebraicMatrix line = new AlgebraicMatrix(2, 4);
			line.addEqautionOfLine(shape[i].toArray(), shape[j].toArray());
			if (line.satisfiesEquation(pack[0].getPoint()) && line.satisfiesEquation(pack[1].getPoint()))
			{
				return true;
			}
		}
		return false;
	}

	//TODO kind of a mess
	private SplittingPackage[] splitPolygon(Polygon3D poly, AlgebraicMatrix lineIntersect)
	{
		RefPoint3D[] shape = poly.getShape();
		ArrayList<SplittingPackage> points = new ArrayList<>();
		int i, j;
		for (i = 0, j = shape.length-1; i < shape.length; j = i++)
		{
			double[] p = splitEdge(shape[i].toArray(), shape[j].toArray(), lineIntersect);
			if (p != null)
			{
				//check if the intersection is on the line segment
				if (VectorCalc.p3_in_line_seg(shape[i].toArray(),
						shape[j].toArray(), p))
				{
					points.add(new SplittingPackage(p, i));
				}
			}
		}
		if (points.size() >= 2)
		{
			//makes proper order
			if (points.get(0).getIndex() > points.get(1).getIndex())
			{
				return new SplittingPackage[]{
						points.get(1),
						points.get(0)
				};
			}
			else
			{
				return new SplittingPackage[]{
						points.get(0),
						points.get(1)
				};
			}
		}
		//not enough intersection relativePoints were found
		return null;
	}

	private double[] splitEdge(double[] e1, double[] e2, AlgebraicMatrix lineIntersect)
	{
		//we need to add another line so +2 length required
		AlgebraicMatrix edgeIntersect = new AlgebraicMatrix(lineIntersect.getRows() + 2, 4);
		edgeIntersect.insertMatrix(lineIntersect);
		edgeIntersect.addEqautionOfLine(e1, e2);
		edgeIntersect.gaussJordandElimination();
		edgeIntersect.determineSolution();
		if (edgeIntersect.getSolutionType() == AlgebraicMatrix.SolutionType.POINT) {
			return edgeIntersect.getPointSolution();
		}
		else
		{
			//there was no useful intersect
			return null;
		}
	}

	@Override
	public void onTick(GXTickEvent.Type t) {
		if (t.equals(GXTickEvent.Type.PostTick))
		{
			globalRedraw = false;
		}
	}
}
