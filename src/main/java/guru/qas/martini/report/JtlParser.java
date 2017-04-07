/*
Copyright 2017 Penny Rohr Curich

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package guru.qas.martini.report;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.util.Stack;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;

@SuppressWarnings("WeakerAccess")
public class JtlParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(JtlParser.class);

	protected final XMLStreamReader reader;
	protected final Writer writer;
	protected final Stack<Sample.Builder> stack;

	protected JtlParser(XMLStreamReader reader, Writer writer) {
		this.reader = reader;
		this.writer = writer;
		this.stack = new Stack<>();
	}

	public void parse() throws XMLStreamException, IOException {
		while (reader.hasNext()) {
			reader.next();
			int event = reader.getEventType();
			switch (event) {
				case XMLStreamConstants.START_ELEMENT:
					handleStartElement();
					break;
				case XMLStreamConstants.CHARACTERS:
					break;
				case XMLStreamConstants.END_ELEMENT:
					handleEndElement();
					break;
			}
		}
		writer.flush();
	}

	protected void handleEndElement() throws IOException {
		String localName = reader.getLocalName();
		if (localName.equals("sample")) {
			Sample.Builder builder = stack.pop();
			Sample sample = builder.build();

			if (!stack.isEmpty()) {
				Sample.Builder parent = stack.peek();
				parent.addSub(sample);
			}
			else {
				String json = sample.getJson();
				writer.append(json).append(System.lineSeparator());
			}
		}
	}

	protected void handleStartElement() throws XMLStreamException {
		String name = reader.getLocalName();
		switch (name) {
			case "testResults":
				handleTestResults();
				break;
			case "sample":
				handleSample();
				break;
			case "responseData":
				handleResponseData();
				break;
		}

	}

	protected void handleTestResults() {
		String version = reader.getAttributeValue(null, "version");
		if (null == version || !version.equals("1.2")) {
			LOGGER.warn("testResults version is {}, this class expectes to work with version 1.2", version);
		}
	}


	protected void handleSample() {
		Sample.Builder builder = Sample.builder();
		stack.push(builder);
	}

	protected void handleResponseData() throws XMLStreamException {
		String elementText = reader.getElementText();

		Object peek = stack.peek();
		checkState(Sample.Builder.class.isInstance(peek), "expected a Sample.Builder on the stack but found %s", peek);
		Sample.Builder builder = Sample.Builder.class.cast(peek);
		builder.setResponseData(elementText);
	}

	public static Builder builder() {
		return new Builder();
	}

	@SuppressWarnings("WeakerAccess")
	public static class Builder {

		String inputLocation;
		String outputPath;

		protected Builder() {
		}

		Builder setInputLocation(String s) {
			this.inputLocation = s;
			return this;
		}

		Builder setOutputPath(String s) {
			this.outputPath = s;
			return this;
		}

		public JtlParser build() throws IOException, XMLStreamException {
			checkState(null != inputLocation, "input URL not specified");
			checkState(null != outputPath, "output path not specified");

			URL url = new URL(inputLocation);
			InputStream in = url.openStream();
			XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
			XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(in);

			FileWriter writer = new FileWriter(outputPath, false);
			return new JtlParser(xmlStreamReader, writer);
		}

	}

	public static void main(String[] args) throws IOException, XMLStreamException {
		checkState(2 == args.length, "specify an input URL and an output File");
		JtlParser application = JtlParser.builder().setInputLocation(args[0]).setOutputPath(args[1]).build();
		application.parse();
	}
}
