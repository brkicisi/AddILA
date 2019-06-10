
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.debug.*;
import com.xilinx.rapidwright.edif.*;
import com.xilinx.rapidwright.util.MessageGenerator;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.device.BELPin;

import com.xilinx.rapidwright.router.Router;
import com.xilinx.rapidwright.util.FileTools;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;

import java.io.FileWriter;

import java.lang.IndexOutOfBoundsException;
import java.io.IOException;

public class DebugProbeRouter {

    /**
     * Modified from ProbeRouter.updateProbeConnections()
     * 
     * Updates a design containing an ILA (integrated logic analyzer) probe
     * connections that already exist in a design.
     * 
     * @param d                 The existing placed and routed design with an ILA.
     * @param probeToTargetNets A map from probe names to desired net names (full
     *                          hierarchical names).
     * @param pblock            An optional pblock (area constraint) to contain
     *                          routing within a certain area.
     */
    public static void my_updateProbeConnections(Design d, Map<String,String> probeToTargetNets, PBlock pblock){
		ArrayList<SitePinInst> pinsToRoute = new ArrayList<>(); 
		for(Entry<String,String> e : probeToTargetNets.entrySet()){
            String hierPinName = e.getKey();
			String cellInstName = EDIFTools.getHierarchicalRootFromPinName(hierPinName);
			EDIFCellInst i = d.getNetlist().getCellInstFromHierName(cellInstName);
			String pinName = hierPinName.substring(hierPinName.lastIndexOf(EDIFTools.EDIF_HIER_SEP)+1);
			EDIFPortInst portInst = i.getPortInst(pinName);
			EDIFNet net = portInst.getNet();
            String parentCellInstName = cellInstName.contains(EDIFTools.EDIF_HIER_SEP) ? cellInstName.substring(0,cellInstName.lastIndexOf(EDIFTools.EDIF_HIER_SEP)) : "";
            Net oldPhysNet = null;
            try{
                oldPhysNet = d.getNetlist().getPhysicalNetFromPin(parentCellInstName, portInst, d);
            } catch(IndexOutOfBoundsException iobe){
                oldPhysNet = null;
            }
			// Find the sink flop
			String hierInstName = cellInstName.contains(EDIFTools.EDIF_HIER_SEP) ? cellInstName.substring(0, cellInstName.lastIndexOf('/')) : ""; 
			EDIFHierPortInst startingPoint = new EDIFHierPortInst(hierInstName, portInst);
			ArrayList<EDIFHierPortInst> sinks = EDIFTools.findSinks(startingPoint);
			if(sinks.size() != 1) {
				System.err.println("ERROR: Currently we only support a single flip flop "
						+ "sink for probe re-routes, found " + sinks.size() + " on " + e.getKey() + ", skipping...");
				continue;
			}
				
			EDIFHierPortInst sinkFlop = sinks.get(0);
            Cell c = d.getCell(sinkFlop.getFullHierarchicalInstName());
            SitePinInst physProbeInPin = null;
            try{
                physProbeInPin = c.unrouteLogicalPinInSite(sinkFlop.getPortInst().getName());
            } catch(NullPointerException npe){
                physProbeInPin = null;
            }
			
			// Disconnect probe from current net
			net.removePortInst(portInst);
			// Unroute the portion of physical route to old probe net
			if(physProbeInPin != null) 
				oldPhysNet.removePin(physProbeInPin,true);
			
			// Connect probe to new net
			String newPortName = "rw_"+ pinName;
			EDIFNet newNet = net.getParentCell().createNet(newPortName);
			newNet.addPortInst(portInst);

			EDIFCellInst parent = d.getNetlist().getCellInstFromHierName(parentCellInstName);
			EDIFHierCellInst parentInst = new EDIFHierCellInst(parentCellInstName, parent);
			EDIFTools.connectDebugProbe(newNet, e.getValue(), newPortName, parentInst, d.getNetlist(), null);
			
			String parentNet = d.getNetlist().getParentNetName(e.getValue());
			Net destPhysNet = d.getNet(parentNet);
			
			// Route the site appropriately
			
			/*String siteWire = c.getSiteWireNameFromLogicalPin(sinkFlop.getPortInst().getName());
			c.getSiteInst().addCTag(destPhysNet, siteWire);
			BELPin inPin = c.getBEL().getPin(c.getPhysicalPinMapping(sinkFlop.getPortInst().getName()));
			BELPin rbel = inPin.getSiteConns().get(0);
			c.getSiteInst().addSitePIP(rbel.getBEL().getName(), "BYP", rbel.getName());
			String siteWireName = rbel.getBEL().getPin("BYP").getSiteWireName();
			c.getSiteInst().addCTag(destPhysNet, siteWireName);*/
			
			String sitePinName = c.getBELName().charAt(0) + "X";
			BELPin inPin = c.getBEL().getPin(c.getPhysicalPinMapping(sinkFlop.getPortInst().getName()));
			c.getSiteInst().routeIntraSiteNet(destPhysNet, c.getSite().getBELPin(sitePinName), inPin);
			
			if(physProbeInPin == null){
				// Previous connection was internal to site, need to route out to site pin
				physProbeInPin = new SitePinInst(false, sitePinName, c.getSiteInst());
			}
			destPhysNet.addPin(physProbeInPin);
			pinsToRoute.add(physProbeInPin);
		}
		
		// Attempt route new net to probe
		// TODO - Should we add a flop?
		Router r = new Router(d);
		if(pblock != null) r.setRoutingPblock(pblock);
		r.routePinsReEntrant(pinsToRoute, false);
	}

    /**
     * This should take in a dcp output by ila inserter and route to it
     * 
     * @param args
     */
    public static void route_marked_debug(String[] args) {

    }

    private static void printHelp() {
        MessageGenerator.briefMessageAndExit("USAGE: <input.dcp> <output.dcp> [<probes.txt>]");
    }

    public static void main(String[] args) {
        // route_marked_debug(args);
        if(args.length < 2)
            printHelp();
        
        Design design = Design.readCheckpoint(args[0]);
        Map<String, String> probe_map;

        if(args.length >= 3){
            probe_map = ProbeRouter.readProbeRequestFile(args[2]);
        }
        else {
            probe_map = new TreeMap<>();
            List<String> debug_nets = ILAInserter.getNetsMarkedForDebug(design);
            try {
                FileWriter fw = new FileWriter("probes.txt", false); // overwrite
                fw.write("# Probe mapping: <full probe net path> -> <full debug net path>\n");
                for (int i = 0; i < debug_nets.size() ; i++) {
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
        }

        my_updateProbeConnections(design, probe_map, null);
        // ProbeRouter.updateProbeConnections(design, probe_map, null);

        design.writeCheckpoint(args[1]);
    }
}
