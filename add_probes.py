# Igi Brkic
# 16 May 2019
# python v3.5.3

import os
import argparse
import sys
import subprocess


def main():
	"""This invokes a java program to add probes to a Vivado design checkpoint with ILA generated using AddILA.java (invoked by add_ila.py).
	
	----
	['-h, '--help']		Show help message.
	<input_dcp>			Input design checkpoint (.dcp) file.
	<output_dcp>		Output dcp file.
	[probes_file]		Input file for probe connections.
	['-q, '--quiet']	Execute quietly.
	"""
# 	['-f, '--force']	Force file overwrite.
	
	parser = argparse.ArgumentParser(description="This invokes a java program to add an ILA to a routed Vivado design checkpoint.")
	parser.add_argument('input_dcp', nargs=1, help="Input design checkpoint (.dcp) file.")
	parser.add_argument('output_dcp', nargs=1, help="Output dcp file.")
	parser.add_argument('probes_file', nargs='?', default=None, help="Input file for probe connections.")
	parser.add_argument('-q', '--quiet', dest='quiet', action='store_true', help="Suppress messages.")
#	parser.add_argument('-f', '--force', dest='force', action='store_true', help="Overwrite file at destination if it exists.")
	args = parser.parse_args()
	
	
	place_and_route_dir = "~/Documents/2019_summer/AddILA/"
	class_path = ".:/nfs/ug/thesis/thesis0/pc2019/Software/RapidWright/RapidWright:$CLASSPATH"
	
	cd_cmd = "cd {}".format(place_and_route_dir)
	java_compile_cmd = "javac DebugProbeRouter.java".format()

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
		probes_file = ""
	else:
		if args.probes_file[0][0] in {'~', '/'}:
			probes_file = " " + args.probes_file
		else:
			probes_file = " " + pwd + "/" + args.probes_file
	
	java_execute_cmd = "java -cp {0} DebugProbeRouter {1} {2}{3}".format(class_path, input_dcp, output_dcp, probes_file)
	
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
