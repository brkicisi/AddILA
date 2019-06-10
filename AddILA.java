
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.debug.*;
import com.xilinx.rapidwright.edif.*;
import com.xilinx.rapidwright.util.MessageGenerator;
import com.xilinx.rapidwright.util.FileTools;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import java.io.IOException;
import java.io.FileWriter;
import java.io.File;

import java.lang.NumberFormatException;


public class AddILA {

    /**
	 * This method will try to connect probes to those nets which are marked for
	 * debug. It is limited by the number of probe wires and makes no guarentees for
	 * ordering.
	 * 
	 * @param design The design to examine
	 * @return The number of probes connected to debug nets.
	 */
/*
	public static int set_probe_locations(Design design) {
		int probeIndex = 0;
		List<String> markedForDebug = ILAInserter.getNetsMarkedForDebug(design);

		/*
		 * figure out how to connect the probes to the things marked for debug
		 * EDIFNetlist edifNetlist = Design.getNetlist(); EDIFNet enet =
		 * Design.getNetlistNetMap().get(s); List<ModuleInst> Design.getModuleInsts();
		 * Collection<Net> Design.getNets(); Net Design.getNet(String name); void
		 * Net.setSource(SitePinInst source); SitePinInst Net.connect(Cell c, String
		 * logicalPinName); List<SitePinInst> Net.getPins()
		 */
/*
		// probe0[i] nets are found in edif_nets HashMap
		EDIFNetlist edif_netlist = design.getNetlist();
		HashMap<String,EDIFCellInst> edif_CIM = edif_netlist.generateCellInstMap();
		HashMap<String,EDIFNet> edif_nets = edif_netlist.generateEDIFNetMap(edif_CIM);
		
		String probe_str[] = {"top/u_ila_0/probe0[", "]"};

		System.out.println();
		System.out.println("debug - i + str : net.name : net : net.source");
		for (String s : markedForDebug) {
			// Net debug_net = design.getNet(s);
			EDIFNet edif_debug = edif_nets.get(s);
			EDIFNet edif_probe = edif_nets.get(probe_str[0] + probeIndex + probe_str[1]);

			System.out.print(probeIndex + " : " + s);
			if(edif_debug != null){
				if(edif_probe != null)
					System.out.print(" - found both");
				else
					System.out.print(" - only found debug");
			}
			else {
				if(edif_probe != null)
					System.out.print(" - only found probe");
				else
					System.out.print(" - found neither");
			}
			System.out.println();
*/			
            // TODO what should I give these fns?
            /*
			EDIFNet tool_debug = EDIFTools.addDebugPortAndNet(
				, // String newDebugNetName
				edif_netlist.getTopCell(), // EDIFCell topCell
				, // EDIFPortInst currPort
				edif_netlist.getTopCellInst() // EDIFCellInst debugCore
				);
		
			EDIFTools.connectDebugProbe(
				tool_debug, // EDIFNet topPortNet
				s, // String routedNetName
                , // String newPortName
// TODO
				tool_debug.getParentCell().getCellInst(String name), // EDIFHierCellInst parentInst
                edif_netlist, // EDIFNetlist n
				EDIFTools.generateCellInstMap(edif_netlist.getTopCellInst()) // HashMap<EDIFCell,ArrayList<EDIFCellInst>> instMap)
            );
            */
/*
			probeIndex++;
        }
		return probeIndex;
    }
*/ 

    private static final String PBLOCK_SWITCH = "--pblock";
    private static final String PRE_ILA_SWITCH = "--ila";

    private static void printHelp() {
        MessageGenerator.briefMessageAndExit("USAGE: <input.dcp> <output.dcp> probe_count probe_depth clk_net " + "["
                + PRE_ILA_SWITCH + " <ila.dcp>]" + " <probes.txt> " + "[" + PBLOCK_SWITCH
                + " 'CLOCKREGION_X0Y10:CLOCKREGION_X5Y14 CLOCKREGION_X0Y0:CLOCKREGION_X3Y9']");
    }

    public static void main(String[] args) {
        // insert ILA
        if (args.length < 6)
            printHelp();

        String[] ila_inserter_args = null;
        String[] probe_router_args = null;

        if (args.length >= 8 && args[5].equals(PRE_ILA_SWITCH)) { // has ila.dcp
            ila_inserter_args = Arrays.copyOfRange(args, 0, 6);
            ila_inserter_args[5] = args[6]; // don't include flag

            if (args.length >= 10 && args[8].equals(PBLOCK_SWITCH)) { // also has pblock
                probe_router_args = new String[5];
                probe_router_args[1] = args[7]; // probe.txt
                probe_router_args[3] = args[8]; // pblock switch
                probe_router_args[4] = args[9]; // pblock region
            } else { // yes ila.dcp, no plock
                probe_router_args = new String[3];
                probe_router_args[1] = args[7]; // probe.txt
            }
        } else if (args.length >= 8 && args[6].equals(PBLOCK_SWITCH)) { // no ila.dcp, yes pblock
            ila_inserter_args = Arrays.copyOfRange(args, 0, 5);
            probe_router_args = new String[5];
            probe_router_args[1] = args[5]; // probe.txt
            probe_router_args[3] = args[6]; // pblock switch
            probe_router_args[4] = args[7]; // pblock region
        } else { // neither flag set
            ila_inserter_args = Arrays.copyOfRange(args, 0, 5);
            probe_router_args = new String[3];
            probe_router_args[1] = args[5]; // probe.txt
        }
        // these are same for all cases
        probe_router_args[0] = args[1]; // input.dcp
        probe_router_args[2] = args[1]; // output.dcp


        MessageGenerator.briefMessage("\n\n** Starting Design Implementor **\n");
        MessageGenerator.briefMessage("args: " + MessageGenerator.createStringFromArray(ila_inserter_args) + "\n");


        // load probes
        int probe_count = 0;
        try {
            probe_count = Integer.parseInt(args[2]);
        } catch (NumberFormatException nfe){
            probe_count = 0;
        }

        Design design = Design.readCheckpoint(args[0]);
        Map<String, String> probe_map = new TreeMap<>();
        List<String> debug_nets = ILAInserter.getNetsMarkedForDebug(design);
        try {
            // FileTools.writeLinesToTextFile()
            FileWriter fw = new FileWriter(probe_router_args[1], false); // overwrite
            fw.write("# Probe mapping: <full probe net path> -> <full debug net path>\n");
            for (int i = 0; i < probe_count && i < debug_nets.size() ; i++) {
                if(i != 0)
                    fw.write("\n");
                String probe = "top/u_ila_0/probe0[" + i + "]";
                fw.write(probe + " " + debug_nets.get(i));
                probe_map.put(probe, debug_nets.get(i));
            }
            fw.close();
		}
		catch(IOException ioe) {
			System.err.println("IOException in : " + ioe.getMessage());
        }
        
        // write ltx file
        // DesignInstrumentor di = new DesignInstrumentor();
        // di.loadInstrumentationDetailsFile(probe_router_args[1]);
        // String ltx_filename = probe_router_args[2].replace(".dcp", ".ltx");
        // di.createLTX(ltx_filename);
        // di.instrumentDesign();

        // insert ILA
        MessageGenerator.briefMessage("\n\n** Starting ILA Inserter **\n");
        MessageGenerator.briefMessage("args: " + MessageGenerator.createStringFromArray(ila_inserter_args) + "\n");
        ILAInserter.main(ila_inserter_args);

        // MessageGenerator.briefMessage("\n\n** Starting Probe Router **\n");
        // MessageGenerator.briefMessage("args: " + MessageGenerator.createStringFromArray(probe_router_args) + "\n");
        // ProbeRouter.main(probe_router_args);
        // design = Design.readCheckpoint(args[2]);
        // DebugProbeRouter.my_updateProbeConnections(design, probe_map);
        // ProbeRouter.updateProbeConnections(design, probe_map)

        // TODO automate place and route

    }
}