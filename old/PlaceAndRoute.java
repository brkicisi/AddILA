
import com.xilinx.rapidwright.util.MessageGenerator;
import com.xilinx.rapidwright.util.FileTools;

import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.List;
import java.lang.Enum;
import java.io.File;

public class PlaceAndRoute {
    public static void doPlaceAndRoute(String input_dcp, String output_dcp, boolean force, boolean quiet,
            boolean place, boolean route, boolean edif, boolean ltx) {
        if(!place && !route)
                MessageGenerator.briefMessageAndExit("Neither place nor route selected. Exiting.");
        List<String> tclCommands = new ArrayList<>();
        String tclFileName = ".place_and_route.tcl";
        String force_str = force ? " -force" : "";
        String quiet_str = quiet ? " -quiet" : "";

        tclCommands.add("source " + FileTools.getRapidWrightPath() + File.separator + FileTools.TCL_FOLDER_NAME
                + File.separator + "rapidwright.tcl -quiet");

        tclCommands.add("open_checkpoint " + input_dcp);
        if(place)
            tclCommands.add("place_design" + quiet_str);
        if(route)
            tclCommands.add("route_design" + quiet_str);
        if(edif)
            tclCommands.add("write_edif" + force_str + " " + output_dcp.replace(".dcp", ".edf"));
        if(ltx)
            tclCommands.add("write_debug_probes" + force_str + " " + output_dcp.replace(".dcp", ".ltx"));
        tclCommands.add("write_checkpoint" + force_str + " " + output_dcp);

        FileTools.writeLinesToTextFile(tclCommands, tclFileName);
        FileTools.runCommand("vivado -mode batch -log vivado.log -journal vivado.jou -source " + tclFileName, true);
    }


    private static final String PLACE_SWITCH = "-p";
    private static final String ROUTE_SWITCH = "-r";
    private static final String FORCE_SWITCH = "-f";
    private static final String QUIET_SWITCH = "-q";

    private static final String TRUE_SWITCH = "true";
    private static final String FALSE_SWITCH = "false";
    private static final String TRUE_OR_FALSE = "{" + TRUE_SWITCH + "|" + FALSE_SWITCH + "}";
    
    private static void printHelp() {
        String msg = "USAGE: <input.dcp> <output.dcp>"
                + " " + PLACE_SWITCH + " " + TRUE_OR_FALSE
                + " " + ROUTE_SWITCH + " " + TRUE_OR_FALSE
                + " " + FORCE_SWITCH + " " + TRUE_OR_FALSE
                + " " + QUIET_SWITCH + " " + TRUE_OR_FALSE;
        
        MessageGenerator.briefMessageAndExit(msg);
    }
    
    enum MyErrors {
        SWITCH, DCP, ARG
    }
    private static void printError(MyErrors err){
        switch(err) {
            case SWITCH:
                MessageGenerator.briefMessageAndExit("Error: Incorrect switches or ordering detected.");
                break;
            case DCP:
                MessageGenerator.briefMessageAndExit("Error: One or more files provided is not of type '*.dcp'.");
                break;
            case ARG:
                MessageGenerator.briefMessageAndExit("Error: One or more arguments was not recognized.");
                break;
        }
    }
    public static void main(String[] args) {
        if (args.length < 10)
            printHelp();

        // The argument list for invoking this is not very flexible because it is
        // expected to be called from 'place_route.py' which implements ArgumentParser
        boolean place = false;
        boolean route = false;
        boolean edif = false;
        boolean ltx = false;
        boolean force = false;
        boolean quiet = false;
        if(!args[2].equals(PLACE_SWITCH) || !args[4].equals(ROUTE_SWITCH)
                || !args[6].equals(FORCE_SWITCH) || !args[8].equals(QUIET_SWITCH))
            printError(MyErrors.SWITCH);

        if(!args[0].contains(".dcp") || !args[1].contains(".dcp"))
            printError(MyErrors.DCP);
        
        if(args[3].equals(TRUE_SWITCH))
            place = true;
        else if(!args[3].equals(FALSE_SWITCH))
            printError(MyErrors.ARG);
        
        if(args[5].equals(TRUE_SWITCH))
            route = true;
        else if(!args[5].equals(FALSE_SWITCH))
            printError(MyErrors.ARG);

        if(args[7].equals(TRUE_SWITCH))
            force = true;
        else if(!args[7].equals(FALSE_SWITCH))
            printError(MyErrors.ARG);
            
        if(args[9].equals(TRUE_SWITCH))
            quiet = true;
        else if(!args[9].equals(FALSE_SWITCH))
            printError(MyErrors.ARG);
            
        doPlaceAndRoute(args[0], args[1], force, quiet, place, route, edif, ltx);
    }
}