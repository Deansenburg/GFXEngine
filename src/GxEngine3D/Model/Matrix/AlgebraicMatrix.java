package GxEngine3D.Model.Matrix;

import DebugTools.TextOutput;
import GxEngine3D.Helper.PlaneCalc;
import GxEngine3D.Helper.VectorCalc;
import GxEngine3D.Model.Plane;

import java.util.ArrayList;
import java.util.Iterator;

public class AlgebraicMatrix extends Matrix{

    double epsilon = 1e-10;

    public enum SolutionType
    {
        UNDEFINED,
        UNSOLVABLE,
        POINT,
        LINE,
        PLANE,
        HIGHER_DIMENSION
    }

    SolutionType solutionType = SolutionType.UNDEFINED;

    public AlgebraicMatrix(int m, int n)
    {
        super(m, n);
    }

    //a plane counts as 1 but a line counts as 2
    public void addEqautionOfLine(double[] p1, double[] p2)
    {
        Plane[] plane = PlaneCalc.getFullPlaneFromLine(p1, p2);
        double[] planeEq1 = VectorCalc.plane_v3_pointForm(plane[0].getNV().toArray(), plane[0].getP());
        double[] planeEq2 = VectorCalc.plane_v3_pointForm(plane[1].getNV().toArray(), plane[1].getP());
        addEqaution(planeEq1);
        addEqaution(planeEq2);
    }

    public void addEqautionOfPlane(Plane plane)
    {
        double[] eq = VectorCalc.plane_v3_pointForm(
                plane.getNV().toArray(),
                plane.getP());
        addEqaution(eq);
    }

    private void swapOrdering()
    {
        //pre swapping to ensure that there is a non 0 value on the diagonal
        int col = 0, start = 0;
        do {
            for (int i = start; i < m; i++) {
                if (matrix[i][col] != 0) {
                    if (start != i) {
                        double[] temp = matrix[i];
                        matrix[i] = matrix[start];
                        matrix[start] = temp;
                    }
                    start++;
                    break;
                }
            }
            col++;
        }while(col < m && col < n);
        TextOutput.println("Swap", 1);
        TextOutput.println(this, 2);
    }

    public void gaussianElimination()
    {
        TextOutput.println("Gaussian Elimination", 1);
        TextOutput.println(this, 2);
        //this may need to happen after every pivot
        swapOrdering();
        for (int i=0;i<m;i++)
        {
            for (int ii=0;ii<n-1;ii++)
            {
                //find leading edge
                if (matrix[i][ii] == 0) continue;//if 0 we skip
                if (matrix[i][ii] != 1) //if 1 we dont need to change to 1
                {
                    TextOutput.println("Set to 1", 1);
                    //turn it to a 1
                    double scale = 1d / matrix[i][ii];
                    matrix[i] = scale(i, scale);
                    matrix[i][ii] = 1;//to avoid double imprecision
                }
                //turn it to a 0
                for (int iii=i+1;iii<m;iii++)
                {
                    if (matrix[iii][ii] == 0) continue;
                    matrix[iii] = add(iii,  scale(i, -matrix[iii][ii]));
                    matrix[iii][ii] = 0;
                }
                break;
            }
        }
    }

    private void cullSmallValues()
    {
        for (int i=0;i<m;i++) {
            for (int ii = 0; ii < n; ii++) {
                if (Math.abs(matrix[i][ii]) < epsilon)
                {
                    matrix[i][ii] = 0;
                }
            }
        }
    }

    public void gaussJordandElimination()
    {
        gaussianElimination();
        swapOrdering();
        for (int i=m-1;i>=0;i--)
        {
            for (int ii=0;ii<n-1;ii++) {
                //find leading edge, we know its in gaussian form so any non 0 value is 1
                if (matrix[i][ii] == 0) continue;
                for (int iii=i-1;iii>=0;iii--)
                {
                    matrix[iii] = add(iii, scale(i, -matrix[iii][ii]));
                    matrix[iii][ii] = 0;
                }
                break;
            }
        }
        cullSmallValues();
        TextOutput.println("GJ Cull", 1);
        TextOutput.println(this, 2);
    }

    public void determineSolution()
    {
        //if every row has only 1 non 0 value, then is point
        boolean unsolvable = false;
        boolean[] pColumns = new boolean[n-1];
        for (int i=0;i<m;i++) {
            boolean foundPivot = false;
            //the last column is not a basic variable
            for (int ii=0;ii<n-1;ii++)
            {
                //for counting pivots
                if (matrix[i][ii] == 0) continue;
                foundPivot = true;
                pColumns[ii] = true;
                break;
            }
            if (!foundPivot)
            {
                //there are all 0's in this row but non 0 at end?
                if (matrix[i][n-1] != 0)
                {
                    unsolvable = true;
                    break;
                }
            }
        }
        if (unsolvable)
        {
            solutionType = SolutionType.UNSOLVABLE;
            return;
        }
        //count free variables and pivots
        int pivot = 0, freeVariable = 0;
        for (boolean b:pColumns)
        {
            if (b)
            {
                pivot++;
            }
            else
            {
                freeVariable++;
            }
        }
        //if there are no free variables and every column has a pivot
        if (freeVariable == 0 && pivot == n-1)
        {
            solutionType = SolutionType.POINT;
        }
        //only 1 free variable mean line?
        else if (freeVariable == 1)
        {
            solutionType = SolutionType.LINE;
        }
        else if (freeVariable == 2)
        {
            solutionType = SolutionType.PLANE;
        }
        else if (freeVariable > 2)
        {
            solutionType = SolutionType.HIGHER_DIMENSION;
        }
        else
        {
            TextOutput.println("Unknown solution type", 0);
            solutionType = SolutionType.UNDEFINED;
        }
    }


    public SolutionType getSolutionType() {
        return solutionType;
    }

    private double[] solveViaBackSubstitution()
    {
        double[] solution = new double[n-1];
        boolean[] sRef = new boolean[n-1];
        ArrayList<Integer> rowIndex = new ArrayList<>();
        int solutions = 0;
        //find already solved parts
        for (int i=0;i<m;i++)
        {
            int variables = 0, lastIndex = 0;
            for (int ii=0;ii<n-1;ii++)
            {
                if (matrix[i][ii] == 1)
                {
                    variables++;
                    lastIndex = ii;
                }
            }
            if (variables == 1)
            {
                solution[lastIndex] = matrix[i][n-1];
                sRef[lastIndex] = true;
                solutions++;
            }
            else if (variables > 1)
            {
                //rows to solve
                rowIndex.add(i);
            }
        }
        //keep going until we have all solutions
        while (solutions < n-1)
        {
            Iterator<Integer> it = rowIndex.iterator();
            while (it.hasNext())
            {
                double[] eq = matrix[it.next()];
                //sum is equal to current end of matrix
                double sum = eq[n-1];
                int unknowns = 0, uIndex = -1;
                for (int i=0;i<n-1;i++)
                {
                    //eg:
                    //2x + 3y = 5
                    //x = 2, y = ?
                    //5-2(2) => 1 => 3y=1
                    //if there is a variable to substitute and we have it
                    if (eq[i] != 0)
                    {
                        if (sRef[i])
                        {
                            sum -= eq[i]*solution[i];
                        }
                        else
                        {
                            //we can have 1 unknown and still solve
                            unknowns++;
                            uIndex = i;
                            if (unknowns > 1)
                            {
                                break;
                            }
                        }
                    }
                }
                //if 0 there is nothing to solve, if >1 then we can solve yet
                if (unknowns == 1)
                {
                    solution[uIndex] = sum / eq[uIndex];
                    sRef[uIndex] = true;
                    solutions++;
                    it.remove();
                }
            }
        }
        return solution;
    }

    public boolean satisfiesEquation(double[] values)
    {
        for (int i=0;i<m;i++)
        {
            double sum = matrix[i][n-1];
            for (int ii=0;ii<n-1;ii++)
            {
                sum -= matrix[i][ii] * values[ii];
            }
            if (Math.abs(sum) > epsilon)
            {
                //if the point satisfies the eq, then the sum should be 0 or "0"
                return false;
            }
        }
        return true;
    }

    public double[] getPointSolution()
    {
        if (solutionType == SolutionType.POINT)
        {
            return solveViaBackSubstitution();
        }
        TextOutput.println("Incorrect type Solution", 0);
        return null;
    }
}
