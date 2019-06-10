# Igi Brkic
# 16 May 2019
# python v3.5.3

import os
import argparse
import sys
import subprocess


def main():
	"""This invokes a java program to place and/or route a design using Vivado tc.
	
	----
	['-h, '--help']		Show help message.
	<input_dcp>			Input design checkpoint (.dcp) file.
	<output_dcp>		Output dcp file.
	['-p, '--place']	Place design.
	['-r, '--route']	Route design.
	['-b, '--both']		Place and route design. This option supercedes -p or -r.
	['-f, '--force']	Force file overwrite.
	['-q, '--quiet']	Execute quietly.
	"""
	
	parser = argparse.ArgumentParser(description="This invokes a java program to place and/or route a design using Vivado tcl.")
	parser.add_argument('input_dcp', nargs=1, help="Input design checkpoint (.dcp) file.")
	parser.add_argument('output_dcp', nargs=1, help="Output dcp file.")
	parser.add_argument('-p', '--place', dest='place', action='store_true', help="Place design.")
	parser.add_argument('-r', '--route', dest='route', action='store_true', help="Route design.")
	parser.add_argument('-b', '--both', dest='place_and_route', action='store_true', help="Place and route design. This option supercedes -p or -r.")
	parser.add_argument('-q', '--quiet', dest='quiet', action='store_true', help="Suppress messages.")
	parser.add_argument('-f', '--force', dest='force', action='store_true', help="Overwrite file at destination if it exists.")
	args = parser.parse_args()
	
	
	if args.place or args.place_and_route:
		place_flag = "-p true"
	else:
		place_flag = "-p false"
		
	if args.route or args.place_and_route:
		route_flag = "-r true"
	else:
		route_flag = "-r false"
	
	if args.force:
		force_flag = "-f true"
	else:
		force_flag = "-f false"
	if args.quiet:
		quiet_flag = "-q true"
	else:
		quiet_flag = "-q false"
	
	
	place_and_route_dir = "~/Documents/2019_summer/AddILA/"
	class_path = ".:/nfs/ug/thesis/thesis0/pc2019/Software/RapidWright/RapidWright"
	
	cd_cmd = "cd {}".format(place_and_route_dir)
	java_compile_cmd = "javac PlaceAndRoute.java".format()

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
	
	java_execute_cmd = "java -cp {0} PlaceAndRoute {1} {2} {3} {4} {5} {6}".format(class_path, input_dcp, output_dcp, place_flag, route_flag, force_flag, quiet_flag)
	
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
