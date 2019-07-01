package TextureGraphics;

import TextureGraphics.Memory.CachedMemoryHandler;
import TextureGraphics.Memory.MemoryHandler;
import org.jocl.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.jocl.CL.*;

public abstract class JoclProgram {

    protected cl_kernel kernel;

    protected cl_context context;
    protected cl_command_queue commandQueue;

    protected boolean profiling = false;
    cl_device_id device;

    //dynamic memory gets refreshed from round to round
    //cached memory stays for the lifetime of the program object, but can be added at anytime
    //static memory does not change after init
    protected MemoryHandler dynamic, immutable, cached;

    //after newest nvidia drivers, the cleanup became a race condition. syncing threads should remove this
    Thread cleanupThread = new Thread();

    protected void start()
    {
        initStaticMemory();
        initDynamicMemory();
        initCachedMemory();
    }

    protected void initStaticMemory()
    {
        immutable = new MemoryHandler(context, commandQueue);
    }

    public void setup()
    {
        initDynamicMemory();
    }

    public void initCachedMemory()
    {
        cached = new CachedMemoryHandler(context, commandQueue);
    }

    protected void initDynamicMemory()
    {
        if (cleanupThread.isAlive()) {
            try {
                cleanupThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        dynamic = new MemoryHandler(context, commandQueue);
    }

    protected void finish()
    {
        cleanupThread = new Thread(new Runnable() {
            @Override
            public void run() {
                dynamic.releaseAll();
            }
        });
        cleanupThread.start();
    }

    public static cl_platform_id createPlatform()
    {
        final int platformIndex = 0;

        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        return platform;
    }

    public static cl_device_id createDevice(cl_platform_id platform)
    {
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        // Obtain the number of devices for the platform
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        // Obtain a device ID
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        return device;
    }

    public static cl_context createContext(cl_device_id device, cl_platform_id platform)
    {
        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Create a context for the selected device
        cl_context context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device},
                null, null, null);

        return context;
    }

    public static cl_command_queue createCommandQueue(cl_context context, cl_device_id device, boolean profiling)
    {
        cl_queue_properties p = new cl_queue_properties();
        if (profiling) {
            p.addProperty(CL_QUEUE_PROPERTIES, CL_QUEUE_PROFILING_ENABLE);
        }

        cl_command_queue commandQueue = clCreateCommandQueueWithProperties(context, device, p, null);

        return commandQueue;
    }

    protected void create(String sourceFile, String kernalProgram, JoclProgram sharedProgram)
    {
        this.profiling = sharedProgram.profiling;
        create(sourceFile, kernalProgram, sharedProgram.context, sharedProgram.commandQueue);
    }

    protected void create(String sourceFile, String kernalProgram)
    {
        cl_platform_id platform = createPlatform();
        cl_device_id device = createDevice(platform);

        cl_context context = createContext(device, platform);

        cl_command_queue commandQueue = createCommandQueue(context, device, this.profiling);

        create(sourceFile, kernalProgram, context, commandQueue);
    }

    private void create(String sourceFile, String kernalProgram, cl_context context, cl_command_queue commandQueue)
    {
        this.context = context;
        this.commandQueue = commandQueue;
        // Program Setup
        String source = readFile(sourceFile);

        // Create the program
        cl_program cpProgram = clCreateProgramWithSource(context, 1,
                new String[]{ source }, null, null);

        // Build the program
        clBuildProgram(cpProgram, 0, null, "-cl-mad-enable", null, null);

        // Create the kernel
        kernel = clCreateKernel(cpProgram, kernalProgram, null);
    }

    private String readFile(String fileName)
    {
        try
        {
            fileName = getClass().getClassLoader().getResource(fileName).getFile();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(fileName)));
            StringBuffer sb = new StringBuffer();
            String line = null;
            while (true)
            {
                line = br.readLine();
                if (line == null)
                {
                    break;
                }
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }
}
