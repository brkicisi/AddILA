# Igi Brkic
# 16 May 2019
# python v3.5.3

import os
import argparse
import sys
import subprocess


def main():
	"""This invokes a java program to add an ILA to a routed Vivado design checkpoint.
	
	----
	['-h, '--help']		Show help message.
	<input_dcp>			Input design checkpoint (.dcp) file.
	<output_dcp>		Output dcp file.
	['-p, '--probe_count']	Probe count.
	['-d, '--probe_depth']	Probe depth.
	['-c, '--clock']	Clock net.
	['-o, '--probes_file' <probes_txt>]	Output file for probe connections.
	['-q, '--quiet']	Execute quietly.
	"""
# 	['-f, '--force']	Force file overwrite.
	
	parser = argparse.ArgumentParser(description="This invokes a java program to add an ILA to a routed Vivado design checkpoint.")
	parser.add_argument('input_dcp', nargs=1, help="Input design checkpoint (.dcp) file.")
	parser.add_argument('output_dcp', nargs=1, help="Output dcp file.")
	parser.add_argument('-p', '--probe_count', nargs=1, dest='probe_count', default=[4], type=int, help="Probe count.")
	parser.add_argument('-d', '--probe_depth', nargs=1, dest='probe_depth', default=[4096], type=int, help="Probe depth.")
	parser.add_argument('-c', '--clock', nargs=1, dest='clk', default="clk_100MHz", help="Clock net.")
	parser.add_argument('-i', '--ila_dcp', nargs=1, dest='ila_dcp', default=None, help="Input design checkpoint file for ILA.")
	parser.add_argument('-o', '--probes_file', nargs=1, dest='probes_file', default=None, help="Output file for probe connections.")
	parser.add_argument('-q', '--quiet', dest='quiet', action='store_true', help="Suppress messages.")
#	parser.add_argument('-f', '--force', dest='force', action='store_true', help="Overwrite file at destination if it exists.")
	args = parser.parse_args()
	
	
	place_and_route_dir = "~/Documents/2019_summer/AddILA/"
	class_path = ".:/nfs/ug/thesis/thesis0/pc2019/Software/RapidWright/RapidWright:$CLASSPATH"
	
	cd_cmd = "cd {}".format(place_and_route_dir)
	java_compile_cmd = "javac AddILA.java".format()

	try:
		if not args.quiet:
			print("pwd")
		pwd = subprocess.check_output("pwd").decode(sys.stdout.encoding).strip()
		if not args.quiet:
			print(cd_cmd)
		os.system(cd_cmd)
	except:
		print("\nERROR: Failed to execute '{}'.".format(cd_cmd))
		return 1

	if args.input_dcp[0][0] in {'~', '/'}:
		input_dcp = args.input_dcp[0]
	else:
		input_dcp = pwd + "/" + args.input_dcp[0]
	if args.output_dcp[0][0] in {'~', '/'}:
		output_dcp = args.output_dcp[0]
	else:
		output_dcp = pwd + "/" + args.output_dcp[0]
	
	if args.probes_file == None:
		end_of_dir = output_dcp.rfind('/')
		if end_of_dir != -1:
			probes_file = output_dcp[:end_of_dir+1] + "probes.txt"
		else:
			probes_file = pwd + "/" + "probes.txt"
	else:
		if args.probes_file[0][0] in {'~', '/'}:
			probes_file = args.probes_file[0]
		else:
			probes_file = pwd + "/" + args.probes_file[0]

	if args.ila_dcp != None:
		if args.ila_dcp[0][0] in {'~', '/'}:
			ila_dcp = " -ila " + args.ila_dcp[0]
		else:
			ila_dcp = " -ila " + pwd + "/" + args.ila_dcp[0]
	else:
		ila_dcp = ""
	
	# no space between 5 and 6 is intentional
	java_execute_cmd = "java -cp {0} AddILA {1} {2} {3} {4} {5}{6} {7}".format(class_path, input_dcp, output_dcp, args.probe_count[0], args.probe_depth[0], args.clk, ila_dcp, probes_file)
	
	try:
		if not args.quiet:
			print(java_compile_cmd)
		os.system(java_compile_cmd)
	except:
		print("\nERROR: Failed to execute '{}'.".format(java_compile_cmd))
		return 1

	try:
		if not args.quiet:
			print(java_execute_cmd)
		os.system(java_execute_cmd)
	except:
		print("\nERROR: Failed to execute '{}'.".format(java_execute_cmd))
		return 1


if __name__ == "__main__":
    main()
