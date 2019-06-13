# my rwpy commands

d = Design.readCheckpoint("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/ILA/tutorial_2/add_ila_out.dcp")

d.getNetlist().getTopCell()

for lib in d.getNetlist().getLibraries():
	print(lib.getName())

work_lib = d.getNetlist().getLibrary("work")

for cell in work_lib.getCells():
	print(cell)

for cell_pair in work_lib.getCellMap().entrySet():
	print(cell_pair.getKey())

cell = work_lib.getCell("design_1_wrapper")

print(work_lib.getCell("tut_2_dsgn_wrapper").getCellInsts())


print(cell.getView())
cell.setView("design_1_wrapper")

hdi_lib = d.getNetlist().getLibrary("hdi_primitives")
uartI = hdi_lib.getCell("IBUF")
print(uartI.getView())



hci_list = ProbeRouter.findILAs(d)
hci_ila = hci_list.get(0)
ci = hci_ila.getInst()
p0i = ci.getPortInst("probe0[0]")
net = pi.getNet()
pi = net.getPortInst("probes[0]")


# d = Design.readCheckpoint("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/ILA/tutorial_2/add_ila_out.dcp")
# d = Design.readCheckpoint("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/test4/ila4_basic.dcp")
d = Design.readCheckpoint("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/jtest/.iii/tut_2_dsgn_ila.dcp")
netlist = d.getNetlist()

hierPinName = "top/u_ila_0/probe0[0]"
cellInstName = EDIFTools.getHierarchicalRootFromPinName(hierPinName)
i = d.getNetlist().getCellInstFromHierName(cellInstName)
pinName = hierPinName[hierPinName.rindex(EDIFTools.EDIF_HIER_SEP)+1:]
portInst = i.getPortInst(pinName);
net = portInst.getNet()
if cellInstName.find(EDIFTools.EDIF_HIER_SEP) != -1:
	parentCellInstName = cellInstName[0:cellInstName.rindex(EDIFTools.EDIF_HIER_SEP)]
else:
	parentCellInstName = ""

# enter fn getPhysicalNetFromPin
parentHierInstName = parentCellInstName
p = portInst

if parentHierInstName == "":
	hierarchicalNetName = p.getNet().getName();
else:
	hierarchicalNetName = parentHierInstName + EDIFTools.EDIF_HIER_SEP + p.getNet().getName()

parentNetMap = d.getNetlist().getParentNetMap()
parentNetName = parentNetMap.get(hierarchicalNetName)
n = d.getNet(parentNetName)

with open("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/AddILA/.parentNetMap.txt", 'w') as f:
	for k in parentNetMap.keys():
		if k.split('/')[0] == "top" and k.split('/')[1] == "u_ila_0":
			f.write(k + "\n\t\t" + parentNetMap.get(k) + "\n\n")

# logicalNet = getNetFromHierName(parentNetName)

logicalNet = getNetFromHierName(hierarchicalNetName)
eprList = logicalNet.getSourcePortInsts(false);


# exit fn getPhysicalNetFromPin
oldPhysNet = d.getNetlist().getPhysicalNetFromPin(parentCellInstName, portInst, d);


cell = d.getNetlist().getTopCell()
cell_top = cell.getCellInst("top")
cell_ila = d.getNetlist().getCell("u_ila_0")
pi = EDIFPortInst(cell_top.getPort("probes"), cell_ila.getNet("probe0"), cell_top)




counter = 0
for entry in parentNetMap:
	splat = entry.split('/')
	if splat[0] == "top" and splat[1] == "u_ila_0":
		print(entry)
		print(parentNetMap.get(entry))
		print("")
		counter += 1
	if counter >= 10:
		break


allCells = []
for c in d.getCells():
	allCells.append(c.getName())

allCells = StringTools.naturalSort(allCells)
with open("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/AddILA/.d_getCells_2.txt", 'w') as f:
	for cn in allCells:
		f.write(cn + "\n")



import os
import subprocess
from com.xilinx.rapidwright.debug import ProbeRouter
from com.xilinx.rapidwright.design import Design
from com.xilinx.rapidwright.util import FileTools
from java.util import Collections
from java.util import HashMap
from pprint import pprint

design = Design.readCheckpoint("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/ILA/tutorial_2/add_ila_out.dcp")
d = design
netlist = design.getNetlist()
ProbeRouter.findILAs(design)
netlist.getCellInstFromHierName("top/u_ila_0").getCellPorts()
net = netlist.getCellInstFromHierName("top/u_ila_0").getPortInst("probe0[0]").getNet()
probesMap = HashMap()
probesMap.put("top/u_ila_0/probe0[0]", "tut_2_dsgn_i/gpio_led/gpio_io_o[0]")

#ProbeRouter.updateProbeConnections(design,probesMap)
import pdb
pdb.run('ProbeRouter.updateProbeConnections(design,probesMap)')


