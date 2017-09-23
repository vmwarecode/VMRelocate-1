/*
 * ****************************************************************************
 * Copyright VMware, Inc. 2010-2016.  All Rights Reserved.
 * ****************************************************************************
 *
 * This software is made available for use under the terms of the BSD
 * 3-Clause license:
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package com.vmware.vm;

import com.vmware.common.annotations.Action;
import com.vmware.common.annotations.Option;
import com.vmware.common.annotations.Sample;
import com.vmware.connection.ConnectedVimServiceBase;
import com.vmware.vim25.*;

import java.util.*;

/**
 * <pre>
 * VMRelocate
 *
 * Used to relocate a linked clone using disk move type
 *
 * <b>Parameters:</b>
 * url            [required] : url of the web service
 * username       [required] : username for the authentication
 * password       [required] : password for the authentication
 * vmname         [required] : name of the virtual machine
 * diskmovetype   [required] : Either of
 *                               [moveChildMostDiskBacking | moveAllDiskBackingsAndAllowSharing]
 * datastorename  [required] : Name of the datastore
 *
 * <b>Command Line:</b>
 * run.bat com.vmware.vm.VMRelocate --url [URLString] --username [User] --password [Password]
 * --vmname [VMName] --diskmovetype [DiskMoveType] --datastorename [Datastore]
 * </pre>
 */
@Sample(name = "vm-relocate", description = "Used to relocate a linked clone using disk move type")
public class VMRelocate extends ConnectedVimServiceBase {
	static final String[] diskMoveTypes = { "moveChildMostDiskBacking",
			"moveAllDiskBackingsAndAllowSharing" };
	private ManagedObjectReference propCollectorRef;

	String vmname = null;
	String diskMoveType = null;
	String datastoreName = null;

	@Option(name = "vmname", description = "name of the virtual machine")
	public void setVmname(String vmname) {
		this.vmname = vmname;
	}

	@Option(name = "diskmovetype", description = "Either of\n"
			+ "[moveChildMostDiskBacking | moveAllDiskBackingsAndAllowSharing]")
	public void setDiskMoveType(String type) {
		check(type, diskMoveTypes);
		this.diskMoveType = type;
	}

	@Option(name = "datastorename", description = "Name of the datastore")
	public void setDatastoreName(String name) {
		this.datastoreName = name;
	}

	void relocate() throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg,
			VmConfigFaultFaultMsg, InsufficientResourcesFaultFaultMsg,
			InvalidDatastoreFaultMsg, FileFaultFaultMsg,
			MigrationFaultFaultMsg, InvalidStateFaultMsg, TimedoutFaultMsg,
			InvalidCollectorVersionFaultMsg {
		// get vm by vmname
		ManagedObjectReference vmMOR = getMOREFs.vmByVMname(vmname,
				propCollectorRef);
		ManagedObjectReference dsMOR = null;
		Map<String, ManagedObjectReference> dsListMor = getMOREFs
				.inContainerByType(serviceContent.getRootFolder(), "Datastore");
		if (dsListMor.containsKey(datastoreName)) {
			dsMOR = dsListMor.get(datastoreName);
		}

		if (dsMOR == null) {
			System.out.println("Datastore " + datastoreName + " Not Found");
			return;
		}

		if (vmMOR != null) {
			VirtualMachineRelocateSpec rSpec = new VirtualMachineRelocateSpec();
			String moveType = diskMoveType;
			if (moveType.equalsIgnoreCase("moveChildMostDiskBacking")) {
				rSpec.setDiskMoveType("moveChildMostDiskBacking");
			} else if (moveType
					.equalsIgnoreCase("moveAllDiskBackingsAndAllowSharing")) {
				rSpec.setDiskMoveType("moveAllDiskBackingsAndAllowSharing");
			}
			rSpec.setDatastore(dsMOR);
			ManagedObjectReference taskMOR = vimPort.relocateVMTask(vmMOR,
					rSpec, null);
			if (getTaskResultAfterDone(taskMOR)) {
				System.out.println("Linked Clone relocated successfully.");
			} else {
				System.out.println("Failure -: Linked clone "
						+ "cannot be relocated");
			}
		} else {
			System.out.println("Virtual Machine " + vmname + " doesn't exist");
		}
	}

	boolean customValidation() {
		boolean flag = true;
		String val = diskMoveType;
		if ((!val.equalsIgnoreCase("moveChildMostDiskBacking"))
				&& (!val.equalsIgnoreCase("moveAllDiskBackingsAndAllowSharing"))) {
			System.out
					.println("diskmovetype option must be either moveChildMostDiskBacking or "
							+ "moveAllDiskBackingsAndAllowSharing");
			flag = false;
		}
		return flag;
	}

	/**
	 * This method returns a boolean value specifying whether the Task is
	 * succeeded or failed.
	 *
	 * @param task
	 *            ManagedObjectReference representing the Task.
	 * @return boolean value representing the Task result.
	 * @throws InvalidCollectorVersionFaultMsg
	 *
	 * @throws RuntimeFaultFaultMsg
	 * @throws InvalidPropertyFaultMsg
	 */
	boolean getTaskResultAfterDone(ManagedObjectReference task)
			throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg,
			InvalidCollectorVersionFaultMsg {

		boolean retVal = false;

		// info has a property - state for state of the task
		Object[] result = waitForValues.wait(task, new String[] { "info.state",
				"info.error" }, new String[] { "state" },
				new Object[][] { new Object[] { TaskInfoState.SUCCESS,
						TaskInfoState.ERROR } });

		if (result[0].equals(TaskInfoState.SUCCESS)) {
			retVal = true;
		}
		if (result[1] instanceof LocalizedMethodFault) {
			throw new RuntimeException(((LocalizedMethodFault) result[1])
					.getLocalizedMessage());
		}
		return retVal;
	}

	boolean check(String value, String[] values) {
		boolean found = false;
		for (String v : values) {
			if (v.equals(value)) {
				found = true;
			}
		}
		return found;
	}

	@Action
	public void run() throws RuntimeFaultFaultMsg,
			InsufficientResourcesFaultFaultMsg, VmConfigFaultFaultMsg,
			InvalidDatastoreFaultMsg, InvalidPropertyFaultMsg,
			FileFaultFaultMsg, InvalidStateFaultMsg, MigrationFaultFaultMsg,
			InvalidCollectorVersionFaultMsg, TimedoutFaultMsg {
		customValidation();
		propCollectorRef = serviceContent.getPropertyCollector();
		relocate();
	}
}
