package Shapes.Shape2D;

import java.awt.*;

/**
 * Created by Dean on 07/01/17.
 */
public class Square extends NSidedPolygon{

    public Square(Color c) {
        super(c);
    }

    @Override
    protected double getSides() {
        return 4;
    }
}
