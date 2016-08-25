var GithubBoard = React.createClass({
  getInitialState: function(){
    return { labelLists: [] }
  },

  componentDidMount: function(){
    var list = ds.record.getList('github-board-labels');
    list.subscribe(function(entries) {
      this.setState({labelLists:entries});
    }.bind(this));

  },

  render: function() {
    var labelLists = this.state.labelLists.map(function(label){
      return <IssueList label={ label } />
    })
    return (
        <div className="githubBoard">
          <div>
            <span>tutorial-board</span>
          </div>
          { labelLists }
        </div>
        )
  }
});

var IssueList = React.createClass({
  getInitialState: function(){
    return {
      issues: [],
      labelColors: { },
      listColors: ds.record.getRecord('github-board-label-colors')
    }
  } ,

  componentDidMount: function(){
    var issueList = ds.record.getList(this.props.label);
    issueList.subscribe(function(entries) {
      this.setState({ issues: entries });

    }.bind(this));

    this.state.listColors.subscribe(function(data){
     this.setState({ labelColors: data });
    }.bind(this), true);

  },

  render: function() {
    issues = this.state.issues.map(function(id){
      return <Issue dsRecord={ id } />
    })
    var divStyle = {
      borderTop: "10px solid #" + this.state.labelColors[this.props.label]
    };
    return (
        <div className={ "issueList " + this.props.label.replace(/\s/g, '-') }>
        <div className="issueListHeader" style={ divStyle } >{ this.props.label }</div>
        { issues }
        </div>
        )
  }
});

var Issue = React.createClass({
  mixins: [ DeepstreamReactMixin ],

  render: function() {
    return <a className="issue" href={ this.state.url }>{ this.state.title }</a>
  }
});

ds = deepstream( 'localhost:6020' ).login({}, function(){
  React.render(<GithubBoard />, document.body);
});

DeepstreamReactMixin.setDeepstreamClient( ds );
