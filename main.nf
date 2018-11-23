@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')
import groovyx.net.http.RESTClient
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.*


/**
 * Creates a channel with resolved fastq urls for a comma separated list of SRA accessions.
 */
def fromSraAccessions(String accessions) {

  def client = new RESTClient('https://www.ebi.ac.uk/ena/')

  def description_locations = []

  client.get(
    path: "data/view/${accessions}&display=xml", 
    contentType: XML) { response, xml ->
      description_locations = xml.RUN.RUN_LINKS.RUN_LINK.XREF_LINK
        .findAll{it.DB.text().equals("ENA-FASTQ-FILES")}
        .ID
        .collect{it.text()}
  }

  def ftp_paths = description_locations.collect{loc -> 
    log.debug("Fetching $loc")
    def http = new HTTPBuilder( loc )
    def result
    http.request(GET, TEXT) { req ->
      response.success = { resp, reader ->
        result = reader.text.readLines()[1] // skip header
                       .split()[1]          // take fastq field
                       .split(';')          // split it for file pairs
      }
    }
    log.debug("Result $result")
    return result
  }

  ftp_paths = ftp_paths
    .collect{ 
      it.collect{path -> file("ftp://$path")}
    }
  return Channel.from(ftp_paths)
}


params.sra_accessions = "SRR1965912,SRR1965913"

fromSraAccessions(params.sra_accessions)
  .set{ch_data}

// the first process will download the files
process test  {

  input:
  file f from ch_data

  script:
  """
  echo $f
  """

}

