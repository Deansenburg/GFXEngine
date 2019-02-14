package TextureGraphics;

import GxEngine3D.Helper.Iterator.ITriangleIterator;
import GxEngine3D.Helper.Iterator.RegularTriangleIterator;
import GxEngine3D.Helper.PolygonClipBoundsChecker;
import GxEngine3D.Model.Matrix.Matrix;
import TextureGraphics.Memory.AsyncJoclMemory;
import TextureGraphics.Memory.JoclMemory;
import TextureGraphics.Memory.Texture.ITexture;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_event;
import org.jocl.cl_mem;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.jocl.CL.*;

//removes the bottleneck of using polygons with more vertices than 3

public class BarycentricGpuRender_v3 extends JoclRenderer {

    int screenWidth, screenHeight;

    BufferedImage image;

    double[] zMapStart;

    cl_event[] matrixEvents = null;
    ArrayList<cl_event> taskEvents;

    ITriangleIterator clipIt = new RegularTriangleIterator();

    //names to retrieve arguments by
    String pixelOut = "Out1", zMapOut = "Out2", screenSize = "ScreenSize";

    boolean cullPolygon = false;

    public BarycentricGpuRender_v3(int screenWidth, int screenHeight)
    {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        create("resources/Kernels/BarycentricTriangleHigherVertex.cl", "drawTriangle");

        super.start();

        zMapStart = new double[screenWidth*screenHeight];
        Arrays.fill(zMapStart, 1);
    }

    @Override
    protected void initStaticMemory() {
        staticMemory = new cl_mem[1];

        setStaticMemoryArg(0, new int[]{screenWidth, screenHeight}, screenSize);
        setupScreenSizeArgs();
    }

    @Override
    public void setup()
    {
        super.setup();
        taskEvents = new ArrayList<>();
        image = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
        setupOutputMemory();
    }

    public void setMatrix(Matrix i, Matrix e)
    {
        JoclMemory m1 = setCachedMemoryArg(null, i.flatten(), CL_MEM_READ_ONLY);
        JoclMemory m2 = setMemoryArg(null, e.flatten(), CL_MEM_READ_ONLY);//cannot be cached as the camera constantly moves

        setupMatrixArgs(m1, m2);

        matrixEvents = new cl_event[]{
                ((AsyncJoclMemory)m1).getFinishedWritingEvent(),
                ((AsyncJoclMemory)m2).getFinishedWritingEvent()
        };
    }

    public void setClipPolygon(double[][] clipPolygon)
    {
        cullPolygon = PolygonClipBoundsChecker.shouldCull(clipPolygon);
        clipIt.iterate(clipPolygon);
    }


    public void render(double[][] polygon, double[][] textureAnchor, ITexture texture)
    {
        if (cullPolygon) return;
        setupTextureArgs(texture);

        double total = 0;
        int num = 0;
        while(clipIt.hasNext())
        {
            int[] index = clipIt.next();
            double density = calcLength(clipIt.get(index[0]), clipIt.get(index[1]), clipIt.get(index[2]));
            if (density > 0)
            {
                total += density;
                num++;
            }
        }

        cl_event task = renderPolygon(polygon, textureAnchor, Math.sqrt(total/num));
        taskEvents.add(task);
    }

    //converts from the form {{x, y, z}, ...} into {{x0, x1, ...}, {y0, y1, ...}, ...}
    private double[][] flip(double[][] array)
    {
        double[][] newArray = new double[array[0].length][array.length];

        for (int i=0;i<array.length;i++)
        {
            for (int ii=0;ii<array[0].length;ii++) {
                newArray[ii][i] = array[i][ii];
            }
        }

        return newArray;
    }

    private cl_event renderPolygon(double[][] polygon, double[][] textureAnchor, double len)
    {
        double localLen = Math.ceil(Math.sqrt(len));

        //ensures that local length is the root of length
        len = localLen*localLen;

        //added a maximum value so that we don't run out of memory
        localLen = Math.min(localLen, 32);
        len = Math.min(len, 1024);

        long globalWorkSize[];
        // Set work size and execute the kernel
        globalWorkSize = new long[]{
                (long)len, (long)len
        };

        long localWorkSize[];
        localWorkSize = new long[]{
                (long)localLen, (long)localLen
        };

        polygon = flip(polygon);
        textureAnchor = flip(textureAnchor);

        cl_event taskEvent = new cl_event();
        cl_event[] writingEvents = setupTriangleArgs(polygon[0], polygon[1], polygon[2], textureAnchor[0], textureAnchor[1], taskEvent);

        int waitingSize = writingEvents.length + matrixEvents.length;
        cl_event[] waitingEvents = new cl_event[waitingSize];

        System.arraycopy(writingEvents, 0, waitingEvents, 0, writingEvents.length);//moves writingEvents into waitingEvents
        System.arraycopy(matrixEvents, 0, waitingEvents, writingEvents.length, matrixEvents.length);//moves matrix events onto the end of writingEvents

        clEnqueueNDRangeKernel(commandQueue, kernel, 2, null,
                globalWorkSize, localWorkSize, waitingEvents.length, waitingEvents, taskEvent);

        return taskEvent;
    }

    public BufferedImage createImage()
    {
        //a taskEvents size of 0 means that we culled all triangles from the render, so we don't need to read the data
        if (taskEvents.size() > 0) {

            DataBufferInt dataBuffer = (DataBufferInt) image.getRaster().getDataBuffer();
            int data[] = dataBuffer.getData();
            cl_event[] events = new cl_event[taskEvents.size()];
            taskEvents.toArray(events);

            clWaitForEvents(events.length, events);
            readData(data);
        }

        finish();
        return image;
    }

    //removed sqrt here as exact values are not needed since we're using avg as an approximation anyway
    private double calcLength(double[] p1, double[] p2, double[] p3)
    {
        //see barycentric test for explanation
        double len = (-p2[0]*p3[1]) - (p1[0]*p2[1]) + (p1[0]*p3[1]) + (p2[0]*p1[1]) + (p3[0]*p2[1]) - (p3[0]*p1[1]);
        len = Math.abs(screenHeight * screenWidth * len * .5);
        return len;
    }

    //JOCL handling functions
    private void readData(int[] out)
    {
        //read image
        clEnqueueReadBuffer(commandQueue, getDynamic(pixelOut).getRawObject(), CL_TRUE, 0,
                Sizeof.cl_uint * screenWidth*screenHeight, Pointer.to(out), 0, null, null);
    }

    //OUTPUT ARGUMENTS
    private void setupOutputMemory()
    {
        recreateOutputMemory(screenWidth*screenHeight);
        setupOutArgs();
    }

    private void recreateOutputMemory(int size)
    {
        setMemoryArg(size * Sizeof.cl_int, CL_MEM_WRITE_ONLY, pixelOut);
        setMemoryArg(zMapStart, CL_MEM_READ_WRITE, zMapOut);
    }

    private void setupOutArgs()
    {
        clSetKernelArg(kernel, 9, Sizeof.cl_mem, getDynamic(pixelOut).getObject());
        clSetKernelArg(kernel, 10, Sizeof.cl_mem, getDynamic(zMapOut).getObject());
    }
    //OUTPUT ARGUMENT END

    private void setupMatrixArgs(JoclMemory iMatrix, JoclMemory eMatrix)
    {
        clSetKernelArg(kernel, 11, Sizeof.cl_mem, iMatrix.getObject());
        clSetKernelArg(kernel, 12, Sizeof.cl_mem, eMatrix.getObject());
    }


    private void setupScreenSizeArgs()
    {
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(getStatic(screenSize)));
    }
    private void setupTextureArgs(ITexture texture)
    {
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, texture.getTexture());
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, texture.getSize());
    }

    private cl_event[] setupTriangleArgs(double[] tX, double[] tY, double[] tZ,
            double[] tAX, double[] tAY, cl_event task)
    {
        cl_event[] events = new cl_event[5];
        JoclMemory m;
        int index = 0;

        //set the triangle's points
        m = setCachedMemoryArg(task, tX, CL_MEM_READ_ONLY);
        events[index++] = ((AsyncJoclMemory)m).getFinishedWritingEvent();
        clSetKernelArg(kernel, 3, Sizeof.cl_mem, m.getObject());

        m = setCachedMemoryArg(task, tY, CL_MEM_READ_ONLY);
        events[index++] = ((AsyncJoclMemory)m).getFinishedWritingEvent();
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, m.getObject());

        m = setCachedMemoryArg(task, tZ, CL_MEM_READ_ONLY);
        events[index++] = ((AsyncJoclMemory)m).getFinishedWritingEvent();
        clSetKernelArg(kernel, 5, Sizeof.cl_mem, m.getObject());

        //set the texture map's points
        m = setCachedMemoryArg(task, tAX, CL_MEM_READ_ONLY);
        events[index++] = ((AsyncJoclMemory)m).getFinishedWritingEvent();
        clSetKernelArg(kernel, 6, Sizeof.cl_mem, m.getObject());

        m = setCachedMemoryArg(task, tAY, CL_MEM_READ_ONLY);
        events[index++] = ((AsyncJoclMemory)m).getFinishedWritingEvent();
        clSetKernelArg(kernel, 7, Sizeof.cl_mem, m.getObject());

        clSetKernelArg(kernel, 8, Sizeof.cl_int, Pointer.to(new int[]{tX.length - 2}));

        return events;
    }
}
