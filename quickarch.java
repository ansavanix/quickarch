import java.util.Scanner;
import java.io.FileWriter;
import java.io.IOException;
class quickarch {
	static String script = "#!/bin/bash\n";
	static String kernelParameters = "";
	static int fam = 1; //First available megabyte on rootDisk
	static int partCount = 0; 
	static String rootDisk;
	static String username;
	static String hostname;
	static String partPrefix;
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
	
	//Disk and partition management functions
	//rootDisk must be set before use.
	static void mklabel(String label) {
		command("parted " + rootDisk + " mklabel " + label);
	}
	static void mkpart(int megabytes) {
		if (megabytes > 0) {
			int endPart = fam + megabytes;
			command("parted " + rootDisk + " mkpart primary " + String.valueOf(fam) + " " + String.valueOf(endPart));
			fam = endPart;
		}
		else if (megabytes == -1) {
			command("parted " + rootDisk + " mkpart primary " + String.valueOf(fam) + " 100%FREE");
			fam = -1;
		}
		
		partCount++;
	}
	
	//partPrefix must be set for these functions.
	static void encrypt(int partNum) {
		command("cryptsetup luksFormat -y -v " + partPrefix + String.valueOf(partNum));
	}
	
	static void cryptopen(int partNum, String cryptname) {
        command("cryptsetup open " + partPrefix + String.valueOf(partNum) + " " + cryptname);
	}
	
	static void mount(int partNum, String mountpoint) {
        command("mount " + partPrefix + String.valueOf(partNum) + " " + mountpoint);
	}
	
	static void mount(String device, String mountpoint) {
        command("mount " + device + " " + mountpoint);
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
		rootDisk = ask("disk device, Example: /dev/sda");
		username = ask("username");
		hostname = ask("hostname");
		scanner.close(); //Close the scanner as we no longer need to use it.
		
		//The script will assume that you have connected to the internet yourself and you boot the Arch Instllation iso in uefi mode.
		command("timedatectl set-ntp true");
		//Create partitions
		mklabel("gpt");
		mkpart(512); //efi partition.
		mkpart(1024); //swap partition.
		mkpart(25600); //root partition.
		mkpart(-1); //home partition.
		
		if (rootDisk.contains("nvme")) partPrefix = rootDisk + "p"; //Add the character 'p' to the rootDisk string because nvme drives suffix pn where n is the partition number to label partitions.
		else partPrefix = rootDisk;
		
		//Format partitions
		command("mkfs.fat -F 32 " + partPrefix + "1");
		command("mkfs.ext4 " + partPrefix + "3");
		encrypt(2);
		encrypt(4);
		cryptopen(2, "cryptswap");
		cryptopen(4, "crypthome");
		command("mkswap /dev/mapper/cryptswap");
		command("mkfs.ext4 /dev/mapper/crypthome");
		
		//Mount partitions
		command("swapon /dev/mapper/cryptswap");
		mount(3, "/mnt");
		command("mkdir /mnt/home");
		mount("/dev/mapper/crypthome", "/mnt/home");
		
		command("pacstrap /mnt base linux linux-firmware nano"); //Install base system.
		
		//Configure system.
		command("genfstab -U /mnt >> /mnt/etc/fstab");
		echo("cryptswap " + partPrefix + "2 none >> /mnt/etc/crypttab");
		echo("crypthome " + partPrefix + "4 none >> /mnt/etc/crypttab");
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
		echo("\"%wheel ALL=(ALL) ALL\" > /mnt/etc/sudoers.d/wheelconf");
		
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
		archroot("mount " + partPrefix + "1 /boot/efi");
		archroot("grub-install --target=x86_64-efi --bootloader-id=GRUB --efi-directory=/boot/efi");
		archroot("grub-mkconfig -o /boot/grub/grub.cfg");
		
		//Alert user the instlalation has been completed.
		echo("Installation Completed?");
		
		writeScript();
	}
}
