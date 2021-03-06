package GxEngine3D.Ordering;

import GxEngine3D.Model.Polygon3D;
import Shapes.IShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dean on 31/12/16.
 */
//orders based on distance to each polygon
public class OrderPolygon extends BaseOrdering{
    @Override
    public List<Integer> order(double[] from, List<Polygon3D> polygons) {
        double[] k = new double[polygons.size()];
        for (int i = 0; i < k.length; i++) {
            k[i] = polygons.get(i).getDist(from);
        }
        // new order for this shapes polygons
        return sortIndex(k);
    }
}
