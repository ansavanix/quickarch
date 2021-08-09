import java.util.Scanner;
import java.io.FileWriter;
import java.io.IOException;
class quickarch {
	static String script = "#!/bin/bash\n";
	static String kernelParameters = "";
	static Scanner scanner = new Scanner(System.in);
	
	//Function to get user input.
	static String ask(String prompt) {
		System.out.println("Enter " + prompt);
		return scanner.nextLine();
	}
	
	//Functions to add commands to the script.
	static void command(String str) {
		script += str + "\n";
	}
	static void archroot(String str) {
		command("arch-chroot /mnt " + str);
	}
	static void install(String str) {
		archroot("pacman -S --noconfirm " + str);
	}
	static void enable(String str) {
		archroot("systemctl enable " + str);
	}
	static void echo(String str) {
		command("echo " + str);
	}
	
	//Function to write the script string to a file.
	static void writeScript() {
		try {
			FileWriter scriptWriter = new FileWriter("install.sh");
			scriptWriter.write(script);
			scriptWriter.close();
			System.out.println("Script has been written to file: install.sh");
			
		} catch (IOException e) {
			System.out.print(e.getMessage());
		}
	}
	//Main function.
	public static void main (String[] args) {
		String rootDisk = ask("disk device, Example: sda");
		if(!rootDisk.substring(0,5).equals("/dev/")) rootDisk = "/dev/" + rootDisk;
		final String username = ask("username");
		final String hostname = ask("hostname");
		scanner.close(); //Close the scanner as we no longer need to use it.
		
		//The script will assume that you have connected to the internet yourself and you boot the Arch Instllation iso in uefi mode.
		command("timedatectl set-ntp true");
		//Create partitions
		command("parted " + rootDisk + " mklabel gpt");
		command("parted " + rootDisk + " mkpart primary 1 512"); //efi partition.
		command("parted " + rootDisk + " mkpart primary 512 2560"); //swap partition.
		command("parted " + rootDisk + " mkpart primary 2560 28160"); //root partition.
		command("parted " + rootDisk + " mkpart primary 28160 100%FREE"); //home partition.
		
		if (rootDisk.contains("nvme")) rootDisk += "p"; //Add the character 'p' to the rootDisk string because nvme drives suffix pn where n is the partition number to label partitions.
		
		//Format partitions
		command("mkfs.fat -F 32 " + rootDisk + "1");
		command("mkfs.ext4 " + rootDisk + "3");
		command("cryptsetup luksFormat -y -v " + rootDisk + "2");
		command("cryptsetup luksFormat -y -v " + rootDisk + "4");
		command("cryptsetup open " + rootDisk + "2 cryptswap");
		command("cryptsetup open " + rootDisk + "4 crypthome");
		command("mkswap /dev/mapper/cryptswap");
		command("mkfs.ext4 /dev/mapper/crypthome");
		
		//Mount partitions
		command("swapon /dev/mapper/cryptswap");
		command("mount " + rootDisk + "3 /mnt");
		command("mkdir /mnt/home");
		command("mount /dev/mapper/crypthome /mnt/home");
		
		command("pacstrap /mnt base linux linux-firmware nano"); //Install base system.
		
		//Configure system.
		command("genfstab -U /mnt >> /mnt/etc/fstab");
		echo("cryptswap " + rootDisk + "2 none >> /mnt/etc/crypttab");
		echo("crypthome " + rootDisk + "4 none >> /mnt/etc/crypttab");
		archroot("ln -sf /usr/share/zoneinfo/America/Los_Angeles /etc/localtime");
		echo("en_US.UTF-8 UTF-8 >> /mnt/etc/locale.gen");
		echo("LANG=en_US.UTF-8 > /mnt/etc/locale.conf");
		archroot("locale-gen");
		echo(hostname + " > /mnt/etc/hostname");
		archroot("hostnamectl set-hostname " + hostname);
		archroot("passwd --lock root");
		
		//User configuration
		install("sudo");
		archroot("useradd -m " + username);
		archroot("usermod -a -G wheel " + username);
		echo("\"%wheel ALL=(ALL) ALL\" > /mnt/etc/sudoers.d/wheel");
		
		archroot("passwd " + username);
		//Network Manager configuration
		install("networkmanager");
		enable("NetworkManager");
		
		//Use a firwall
		install("ufw");
		archroot("ufw enable");
		enable("ufw");
		
		//Install bootloader (grub)
		install("grub efibootmgr");
		archroot("mkdir /boot/efi");
		archroot("mount " + rootDisk + "1 /boot/efi");
		archroot("grub-install --target=x86_64-efi --bootloader-id=GRUB --efi-directory=/boot/efi");
		archroot("grub-mkconfig -o /boot/grub/grub.cfg");
		
		//Alert user the instlalation has been completed.
		echo("Installation Completed?");
		
		writeScript();
	}
}
