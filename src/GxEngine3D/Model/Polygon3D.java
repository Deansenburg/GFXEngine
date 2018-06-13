package GxEngine3D.Model;

import java.awt.Color;
import GxEngine3D.CalculationHelper.DistanceCalc;
import GxEngine3D.Camera.Camera;
import GxEngine3D.Lighting.Light;
import GxEngine3D.Model.Matrix.Matrix;
import GxEngine3D.View.ViewHandler;
import Shapes.BaseShape;

public class Polygon3D {
	Color c;
	private RefPoint3D[] shape;
	boolean draw = true;

	BaseShape belongsTo;
	
	public Polygon3D(RefPoint3D[] shape, Color c, BaseShape bTo) {
		this.shape = shape;
		this.c = c;
		belongsTo = bTo;
	}

	public Polygon2D updatePolygon(Camera c, Light l, ViewHandler vHandler) {
		Polygon2D screenPoly = new Polygon2D(new double[getShape().length],
				new double[getShape().length], this.c, vHandler,
				belongsTo);

		RefPoint3D[] shp = getShape();
		double[] newX = new double[shp.length];
		double[] newY = new double[shp.length];
		draw = true;

		Matrix projectionMatrix = vHandler.getProjectionMatrix();
		Matrix cameraMatrix = c.getMatrix();

		for (int i = 0; i < shp.length; i++) {
			double[] p = shp[i].toArray();
			p = cameraMatrix.pointMultiply(p);
			p = projectionMatrix.pointMultiply(p);

			//TODO if at least one of these points is within the frustum we should clip the polygon and render
			if (p[0] < -1 || p[0] > 1 || p[1] < -1 || p[1] > 1 || p[2] < -1 || p[2] > 1)
			{
				draw = false;
				break;
			}

			//translates range(-1, 1) into (0, 1)
			newX[i] = ((p[0] + 1) * 0.5 * vHandler.getView().getWidth());
			newY[i] = (1 - (p[1] + 1) * 0.5) * vHandler.getView().getHeight();
		}

		screenPoly.draw = draw;
		if (draw) {
			Plane lPlane = new Plane(this);
			//centre being calculated at object init won't work as the shape would not have been translated yet
			lPlane.setP(findCentre());
			screenPoly.lighting = belongsTo.getLighting().doLighting(l, lPlane, c);
			screenPoly.updatePolygon(newX, newY);
		}
		return screenPoly;
	}

	public double[] findCentre() {
		double avX = 0, avY = 0, avZ = 0;
		for (RefPoint3D p : shape) {
			avX += p.X();
			avY += p.Y();
			avZ += p.Z();
		}

		avX /= shape.length;
		avY /= shape.length;
		avZ /= shape.length;
		return new double[]{avX, avY, avZ};
	}


	public double getDist(double[] from) {
		double total = 0;
		for (int i = 0; i < getShape().length; i++) {
			// System.out.println(GetDistanceToP(i));
			total += DistanceCalc.getDistance(from, getShape()[i].toArray());
		}
		return total / getShape().length;
	}

	public Polygon3D[] splitAlong(SplittingPackage[] pack)
	{
		Polygon3D[] pArr = new Polygon3D[2];
		int start = pack[0].index;
		int end  = pack[1].index;
		int cur = start;

		int size = end-start;
		RefPoint3D[] shape = new RefPoint3D[size+2];//for the two split points
		//add start of split
		shape[0] = new RefPoint3D(pack[0].getPoint());
		for (int i=0;i<size;i++)
		{
			shape[i+1] = this.shape[cur];
			cur++;
			if (cur >= this.shape.length)
			{
				cur = 0;
			}
		}
		//add end of split, technically size+2 -1
		shape[size+1] = new RefPoint3D(pack[1].getPoint());
		pArr[0] = new Polygon3D(shape, c, belongsTo);
		size = start+(this.shape.length-end);
		shape = new RefPoint3D[size+2];
		cur = end;
		//i = (start - 0) + (length-end)
		//add start of split
		shape[0] = new RefPoint3D(pack[1].getPoint());
		for(int i=0;i<size;i++)
		{
			shape[i+1] = this.shape[cur];
			cur++;
			if (cur >= this.shape.length)
			{
				cur = 0;
			}
		}
		//add end of split
		shape[size+1] = new RefPoint3D(pack[0].getPoint());
		pArr[1] = new Polygon3D(shape, c, belongsTo);
		return pArr;
	}

	@Override
	public String toString() {
		String s = "";
		for (RefPoint3D dp : getShape()) {
			s += dp.toString() + " ";
		}
		return s;
	}

	public RefPoint3D[] getShape() {
		return shape;
	}


	public BaseShape getBelongsTo() {
		return belongsTo;
	}

	public boolean canDraw()
	{
		return draw;
	}
}
