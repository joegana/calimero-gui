/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2016, 2017 B. Malinowsky

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

    Linking this library statically or dynamically with other modules is
    making a combined work based on this library. Thus, the terms and
    conditions of the GNU General Public License cover the whole
    combination.

    As a special exception, the copyright holders of this library give you
    permission to link this library with independent modules to produce an
    executable, regardless of the license terms of these independent
    modules, and to copy and distribute the resulting executable under terms
    of your choice, provided that you also meet, for each linked independent
    module, the terms and conditions of the license of that module. An
    independent module is a module which is not derived from or based on
    this library. If you modify this library, you may extend this exception
    to your version of the library, but you are not obligated to do so. If
    you do not wish to do so, delete this exception statement from your
    version.
*/

package tuwien.auto.calimero.gui;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwtChecker
{
	private static final String swtGroupId = "org.eclipse.swt.";
	private static final String swtVersion = "4.6.1";
	private static final String baseDownloadUrl = //
			"https://github.com/maven-eclipse/maven-eclipse.github.io/raw/master/maven/org/eclipse/swt";

	private final Logger logger;

	public static void main(final String[] args)
	{
		if (isSwtOnClasspath())
			Main.main(new String[0]);
		else {
			final SwtChecker checker = new SwtChecker();
			try {
				checker.downloadToLibDir();
			}
			catch (IOException | URISyntaxException | RuntimeException e) {
				checker.logger.error("Failed add SWT library", e);
			}
		}
	}

	public SwtChecker()
	{
		logger = LoggerFactory.getLogger("swt-checker");
	}

	private enum Platform {
		Unknown,
		Linux_x86,
		Linux_x86_64,
		MacOS,
		Win_x86,
		Win_x86_64,
	}

	public static boolean isSwtOnClasspath()
	{
		try {
			// this already fails if swt is not available
			final Class<?> swt = Class.forName("org.eclipse.swt.SWT");

			final Object instance = swt.getConstructor().newInstance();
			final Boolean loadable = invoke(swt, instance, "isLoadable");
			final Integer v = invoke(swt, instance, "getVersion");
			final String platform = invoke(swt, instance, "getPlatform");
			if (!loadable)
				System.err.println("SWT library mismatch, version " + v + ", platform " + platform);
			return loadable;
		}
		catch (final Exception e) {
			return false;
		}
	}

	public void downloadToLibDir() throws IOException, URISyntaxException
	{
		logger.warn("No loadable SWT library on classpath (maybe first start?), trying to download SWT ...");
		final Platform platform = platform();
		logger.info("Detected OS: {}", platform);

		final String folder = swtGroupId + swtPlatformId(platform);
		final String jarName = jarName(platform);
		final String path = String.join("/", baseDownloadUrl, folder, swtVersion, jarName);
		final URI link = new URI(path);
		logger.info("Download {}", link);

		final Path dest = Paths.get(libDir(), "swt.jar");
		logger.info("Save to {}", dest.normalize().toAbsolutePath());

		download(link, dest);
		logger.info("Success, please restart the application!");
	}

	private Platform platform()
	{
		final String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
		// at first try to find out jvm bitness, fallback to OS bitness
		String arch = System.getProperty("sun.arch.data.model");
		if (arch == null)
			arch = System.getProperty("os.arch");

		final boolean is64bit = "64".equals(arch) || "amd64".equals(arch) || "x86_64".equals(arch);
		logger.info("Architecture {}", arch);
		if (os.indexOf("win") >= 0)
			return is64bit ? Platform.Win_x86_64 : Platform.Win_x86;
		if (os.indexOf("mac") >= 0)
			return Platform.MacOS;
		if (os.indexOf("linux") >= 0)
			return is64bit ? Platform.Linux_x86_64 : Platform.Linux_x86;
		return Platform.Unknown;
	}

	private static String swtPlatformId(final Platform p)
	{
		switch (p) {
		case Linux_x86:
			return "gtk.linux.x86";
		case Linux_x86_64:
			return "gtk.linux.x86_64";
		case MacOS:
			return "cocoa.macosx.x86_64";
		case Win_x86:
			return "win32.win32.x86";
		case Win_x86_64:
			return "win32.win32.x86_64";
		case Unknown:
			return "";
		}
		return "";
	}

	private static String jarName(final Platform p)
	{
		return swtGroupId + swtPlatformId(p) + "-" + swtVersion + ".jar";
	}

	private static String libDir() throws URISyntaxException
	{
		final URL url = SwtChecker.class.getResource("SwtChecker.class");
		URI uri = url.toURI();
		if (url.getProtocol().equals("jar")) {
			// remove the jar parts manually, otherwise we have to create a zip file system
			final String p = url.getPath();
			uri = new URI(p.substring(0, p.indexOf('!')));
		}
		final Path path = Paths.get(uri);
		// remove the file name with extension
		final Path dir = Optional.ofNullable(path.getRoot())
				.map(root -> root.resolve(path.subpath(0, path.getNameCount() - 1))).orElse(Paths.get(""));
		return dir.toString();
	}

	private static void download(final URI link, final Path to) throws IOException
	{
		final URL url = link.toURL();
		try (final ReadableByteChannel rbc = Channels.newChannel(url.openStream());
				final FileOutputStream os = new FileOutputStream(to.toFile())) {
			os.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		}
	}

	private static <T> T invoke(final Class<?> swt, final Object instance, final String method)
		throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
		InvocationTargetException
	{
		final Method m = swt.getMethod(method);
		@SuppressWarnings("unchecked")
		final T result = (T) m.invoke(instance);
		return result;
	}
}
